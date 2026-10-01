package fr.cnrs.lacito.matchbox.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.cnrs.lacito.matchbox.assist.prompt.PromptBuilder;
import fr.cnrs.lacito.matchbox.assist.provider.AssistException;
import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest;
import fr.cnrs.lacito.matchbox.assist.provider.ChatResponse;
import fr.cnrs.lacito.matchbox.assist.provider.CompletionProvider;
import fr.cnrs.lacito.matchbox.assist.suggestion.EditLocator;
import fr.cnrs.lacito.matchbox.assist.suggestion.TextEdit;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The assistant's tasks: inline completion, explanation of a parse error, review of a passage.
 * Every proposed change is checked with the grammars of the file before it is returned.
 *
 * <p>The caller is responsible for the privacy decision (whether text may be sent to the
 * provider) before calling these methods.
 */
public final class AssistService {

    private static final Logger logger = Logger.getLogger(AssistService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Set to true to log the full prompts (development only: they contain the user's notes). */
    static final boolean LOG_PROMPTS = Boolean.getBoolean("matchbox.assist.logPrompts");

    private final CompletionProvider fast;
    private final CompletionProvider smart;
    private final EditValidator validator;
    private final AssistOptions options;
    private final Map<String, Explanation> explanations = new ConcurrentHashMap<>();

    /**
     * @param fast  model used for inline completion
     * @param smart model used for explanations and reviews
     */
    public AssistService(CompletionProvider fast, CompletionProvider smart, EditValidator validator, AssistOptions options) {
        this.fast = fast;
        this.smart = smart;
        this.validator = validator;
        this.options = options;
    }

    public AssistOptions options() {
        return options;
    }

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    /** A completion at the cursor, or empty if the model has nothing to add or its text does not parse. */
    public CompletableFuture<Optional<Completion>> complete(AssistContext ctx) {
        ChatRequest request = PromptBuilder.complete(ctx, options.contextBefore(), options.contextAfter(),
            options.maxCompletionLines());
        return call(fast, request).thenCompose(response -> {
            String text = cleanCompletion(response.text(), ctx.text(), ctx.caret(), options.maxCompletionLines());
            if (text.isBlank()) {
                return CompletableFuture.completedFuture(Optional.<Completion>empty());
            }
            TextEdit insertion = TextEdit.insert(ctx.caret(), text);
            return validator.checkAsync(ctx.file(), ctx.text(), List.of(insertion), ctx.grammars())
                .thenApply(result -> {
                    if (!result.accepted()) {
                        logger.fine(() -> "Completion rejected by the grammars: " + result.newErrors());
                        return Optional.<Completion>empty();
                    }
                    return Optional.of(new Completion(text, result.fixedErrors()));
                });
        });
    }

    /** Removes what models add around a completion: code fences, markers, echo of the line, extra lines. */
    static String cleanCompletion(String raw, String text, int caret, int maxLines) {
        String s = raw == null ? "" : raw;
        if (s.strip().startsWith("```")) {
            s = s.strip();
            int firstNewline = s.indexOf('\n');
            s = firstNewline < 0 ? "" : s.substring(firstNewline + 1);
            if (s.stripTrailing().endsWith("```")) {
                s = s.stripTrailing();
                s = s.substring(0, s.length() - 3);
                // the line break before the closing fence
                if (s.endsWith("\n")) {
                    s = s.substring(0, s.length() - 1);
                }
            }
        }
        s = s.replace(PromptBuilder.CURSOR, "");
        // echo of the beginning of the current line
        int lineStart = caret == 0 ? 0 : text.lastIndexOf('\n', caret - 1) + 1;
        String linePrefix = text.substring(lineStart, caret);
        if (!linePrefix.isBlank() && s.startsWith(linePrefix)) {
            s = s.substring(linePrefix.length());
        }
        // limit the number of lines
        String[] lines = s.split("\n", -1);
        if (lines.length > maxLines) {
            s = String.join("\n", java.util.Arrays.copyOf(lines, maxLines)) + "\n";
        }
        // avoid doubling the line break that follows the cursor
        if (caret < text.length() && text.charAt(caret) == '\n' && s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        s = s.replaceAll("[ \\t]+$", "");
        return s.isBlank() ? "" : s;
    }

    // ------------------------------------------------------------------
    // Explanation
    // ------------------------------------------------------------------

    public CompletableFuture<Explanation> explain(AssistContext ctx, ParseError error) {
        String key = explanationKey(ctx, error);
        Explanation cached = explanations.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        ChatRequest request = PromptBuilder.explain(ctx, error, options.language());
        int offset = PromptBuilder.errorOffset(ctx.text(), error);
        return call(smart, request).thenCompose(response -> {
            JsonNode json = parseJson(response.text());
            String explanation = json.path("explanation").asText("").strip();
            if (explanation.isEmpty()) {
                throw new AssistException("The model returned no explanation");
            }
            String original = json.path("original").asText("");
            String replacement = json.path("replacement").asText("");
            if (original.isEmpty()) {
                return CompletableFuture.completedFuture(new Explanation(explanation, Optional.empty(), ""));
            }
            // the correction must be near the error
            int lineSpan = 2000;
            Optional<TextEdit> edit = EditLocator.locate(ctx.text(), offset - lineSpan, offset + lineSpan,
                original, replacement, offset);
            if (edit.isEmpty()) {
                return CompletableFuture.completedFuture(new Explanation(explanation, Optional.empty(),
                    "The proposed correction could not be located in the text."));
            }
            return validator.checkAsync(ctx.file(), ctx.text(), List.of(edit.get()), ctx.grammars())
                .thenApply(result -> result.accepted() && result.fixedErrors() > 0
                    ? new Explanation(explanation, edit, "")
                    : new Explanation(explanation, Optional.empty(),
                        "The proposed correction is not offered: it would not fix the error or would introduce other errors."));
        }).thenApply(e -> {
            explanations.put(key, e);
            return e;
        });
    }

    private static String explanationKey(AssistContext ctx, ParseError error) {
        String text = ctx.text();
        int offset = PromptBuilder.errorOffset(text, error);
        int from = Math.max(0, offset - 600);
        int to = Math.min(text.length(), offset + 600);
        return error.getGrammarId() + "|" + error.getMessage() + "|" + text.substring(from, to).hashCode() + "|" +
            ctx.prefix().key();
    }

    // ------------------------------------------------------------------
    // Review
    // ------------------------------------------------------------------

    /** Reviews {@code text[start, end)}. */
    public CompletableFuture<ReviewResult> review(AssistContext ctx, int start, int end) {
        ChatRequest request = PromptBuilder.review(ctx, start, end, options.language());
        return call(smart, request).thenCompose(response -> {
            JsonNode json = parseJson(response.text());
            List<ReviewResult.ReviewEdit> located = new ArrayList<>();
            int unlocated = 0;
            List<TextEdit> taken = new ArrayList<>();
            for (JsonNode e : json.path("edits")) {
                Optional<TextEdit> edit = EditLocator.locate(ctx.text(), start, end,
                    e.path("original").asText(""), e.path("replacement").asText(""), -1);
                if (edit.isEmpty() || overlaps(edit.get(), taken)) {
                    unlocated++;
                    continue;
                }
                taken.add(edit.get());
                located.add(new ReviewResult.ReviewEdit(edit.get(), e.path("explanation").asText("").strip(),
                    e.path("kind").asText("format"), 0));
            }
            return validateAll(ctx, located, unlocated);
        });
    }

    private CompletableFuture<ReviewResult> validateAll(AssistContext ctx, List<ReviewResult.ReviewEdit> edits, int unlocated) {
        List<CompletableFuture<ReviewResult.ReviewEdit>> checks = new ArrayList<>();
        List<ReviewResult.ReviewEdit> rejected = java.util.Collections.synchronizedList(new ArrayList<>());
        for (ReviewResult.ReviewEdit e : edits) {
            checks.add(validator.checkAsync(ctx.file(), ctx.text(), List.of(e.edit()), ctx.grammars())
                .thenApply(r -> {
                    if (!r.accepted()) {
                        rejected.add(e);
                        return null;
                    }
                    return new ReviewResult.ReviewEdit(e.edit(), e.explanation(), e.kind(), r.fixedErrors());
                }));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(x -> {
            List<ReviewResult.ReviewEdit> accepted = checks.stream().map(CompletableFuture::join)
                .filter(java.util.Objects::nonNull).toList();
            return new ReviewResult(accepted, List.copyOf(rejected), unlocated);
        });
    }

    private static boolean overlaps(TextEdit edit, List<TextEdit> others) {
        return others.stream().anyMatch(o -> edit.start() < o.end() && o.start() < edit.end()
            || edit.start() == o.start());
    }

    // ------------------------------------------------------------------
    // Connection test
    // ------------------------------------------------------------------

    /** Sends a tiny request; completes with the answer, or fails with the provider's error. */
    public static CompletableFuture<String> test(CompletionProvider provider) {
        return call(provider, PromptBuilder.test()).thenApply(ChatResponse::text);
    }

    // ------------------------------------------------------------------
    // Calls
    // ------------------------------------------------------------------

    private static CompletableFuture<ChatResponse> call(CompletionProvider provider, ChatRequest request) {
        long started = System.nanoTime();
        if (LOG_PROMPTS) {
            logger.info("Prompt (" + request.task() + "):\n" + request.user());
        }
        return provider.chat(request).whenComplete((response, error) -> {
            long ms = (System.nanoTime() - started) / 1_000_000;
            String who = provider.config().kind().id() + "/" + provider.config().model();
            if (error != null) {
                logger.log(Level.INFO, "Assistant " + request.task() + " with " + who + " failed after " + ms +
                    " ms: " + error.getMessage());
            } else {
                // never the text: field notes may contain personal data
                logger.info("Assistant " + request.task() + " with " + who + ": " + request.length() +
                    " chars sent, " + response.text().length() + " received, " + ms + " ms, tokens in/out/cached " +
                    response.inputTokens() + "/" + response.outputTokens() + "/" + response.cachedTokens() +
                    (response.truncated() ? ", truncated" : ""));
            }
        });
    }

    /** The JSON object of an answer, tolerating text or a code fence around it. */
    static JsonNode parseJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new AssistException("The model did not return the expected JSON answer");
        }
        try {
            return MAPPER.readTree(text.substring(start, end + 1));
        } catch (IOException e) {
            throw new AssistException("The model returned an unreadable JSON answer", e);
        }
    }
}
