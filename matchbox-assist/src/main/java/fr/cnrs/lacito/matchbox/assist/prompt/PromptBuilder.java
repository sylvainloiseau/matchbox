package fr.cnrs.lacito.matchbox.assist.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.cnrs.lacito.matchbox.assist.AssistContext;
import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest;
import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest.Effort;
import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest.OutputSchema;
import fr.cnrs.lacito.matchbox.assist.suggestion.TokenNames;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the requests of the three tasks. Each request is the shared {@link PromptPrefix}
 * (system) followed by a task-specific user message.
 */
public final class PromptBuilder {

    public static final String CURSOR = "⟦CURSOR⟧";
    public static final String ERROR_MARK = "⟦ERROR⟧";

    /** Lines shown before and after a parse error to explain. */
    static final int ERROR_CONTEXT_LINES = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromptBuilder() {}

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    public static ChatRequest complete(AssistContext ctx, int charsBefore, int charsAfter, int maxLines) {
        String text = ctx.text();
        int caret = Math.min(ctx.caret(), text.length());
        int from = lineStartBefore(text, Math.max(0, caret - charsBefore));
        int to = lineEndAfter(text, Math.min(text.length(), caret + charsAfter));
        String user = """
            Task: continue the text at the cursor.

            Write only the text to insert at %s: no explanation, no quotes, no Markdown code fence, \
            and do not repeat the text before the cursor. Complete the current line or block \
            according to the notation in use, and stop at the end of the block (at most %d lines). \
            If nothing sensible can be added, answer with nothing at all.

            File: %s
            %s
            <text>
            %s%s%s
            </text>""".formatted(CURSOR, maxLines, ctx.file().displayName(), errorsNear(ctx, caret, 5),
                text.substring(from, caret), CURSOR, text.substring(caret, to));
        return new ChatRequest("complete", ctx.prefix().blocks(), user, 2048, Effort.LOW, Optional.empty());
    }

    // ------------------------------------------------------------------
    // Explanation of a parse error
    // ------------------------------------------------------------------

    public static ChatRequest explain(AssistContext ctx, Diagnostic error, String language) {
        String text = ctx.text();
        int offset = errorOffset(text, error);
        int line = lineOf(text, offset);
        int from = lineStart(text, Math.max(1, line - ERROR_CONTEXT_LINES));
        int to = lineEndAfter(text, lineStart(text, line + ERROR_CONTEXT_LINES));
        GrammarPlugin grammar = grammarOf(ctx, error);
        String expected = error.expected().isEmpty() ? "unknown"
            : error.expected().stream().map(t -> TokenNames.readable(grammar, t)).collect(Collectors.joining(", "));
        String user = """
            Task: explain a parse error to the author, in %s.

            The grammar "%s" reported: %s
            Position: line %d, column %d. Expected at this position: %s.
            The marker %s shows where the parser stopped.

            <text>
            %s%s%s
            </text>

            Answer with a JSON object:
            - "explanation": 2 to 4 plain sentences saying what is wrong and how to write it \
            correctly according to the notation; no parser jargon (no "token", "input", "rule").
            - "original" and "replacement": a minimal correction, as an exact passage of the text \
            (without the marker) and its corrected version; use empty strings if the correction \
            needs information that only the author has.""".formatted(
                language, grammar == null ? error.grammarId() : grammar.getDisplayName(), error.message(),
                error.line(), error.column(), expected, ERROR_MARK,
                text.substring(from, offset), ERROR_MARK, text.substring(offset, Math.max(offset, to)));
        return new ChatRequest("explain", ctx.prefix().blocks(), user, 4096, Effort.LOW,
            Optional.of(new OutputSchema("explanation", explanationSchema())));
    }

    static ObjectNode explanationSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode props = schema.putObject("properties");
        props.putObject("explanation").put("type", "string");
        props.putObject("original").put("type", "string");
        props.putObject("replacement").put("type", "string");
        schema.putArray("required").add("explanation").add("original").add("replacement");
        return schema;
    }

    // ------------------------------------------------------------------
    // Review
    // ------------------------------------------------------------------

    public static ChatRequest review(AssistContext ctx, int start, int end, String language) {
        String text = ctx.text();
        String user = """
            Task: review a passage of the notes and propose corrections.

            Look for: passages that do not follow the notation (they would not parse); \
            inconsistencies with the author's conventions shown in the examples (gloss \
            abbreviations, separators, number of morphemes and glosses); formatting problems. \
            Do not rewrite correct text, do not translate, do not add data.
            %s
            <passage>
            %s
            </passage>

            Answer with a JSON object {"edits": [...]}, at most 10 edits, each with:
            - "original": an exact, short passage of the text to change (copied character for \
            character, unique in the passage);
            - "replacement": its corrected version;
            - "explanation": one sentence in %s;
            - "kind": "syntax", "consistency" or "format".
            Answer {"edits": []} if nothing needs to change.""".formatted(
                errorsIn(ctx, start, end), text.substring(start, end), language);
        return new ChatRequest("review", ctx.prefix().blocks(), user, 8192, Effort.MEDIUM,
            Optional.of(new OutputSchema("review", reviewSchema())));
    }

    static ObjectNode reviewSchema() {
        ObjectNode edit = objectSchema();
        ObjectNode props = edit.putObject("properties");
        props.putObject("original").put("type", "string");
        props.putObject("replacement").put("type", "string");
        props.putObject("explanation").put("type", "string");
        ObjectNode kind = props.putObject("kind");
        kind.put("type", "string");
        kind.putArray("enum").add("syntax").add("consistency").add("format");
        edit.putArray("required").add("original").add("replacement").add("explanation").add("kind");

        ObjectNode schema = objectSchema();
        ObjectNode edits = schema.putObject("properties").putObject("edits");
        edits.put("type", "array");
        edits.set("items", edit);
        schema.putArray("required").add("edits");
        return schema;
    }

    /** A connection test: a tiny request. */
    public static ChatRequest test() {
        return new ChatRequest("test", List.of(new ChatRequest.SystemBlock("Answer with one word.", false)),
            "Say OK.", 256, Effort.LOW, Optional.empty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ObjectNode objectSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    static GrammarPlugin grammarOf(AssistContext ctx, Diagnostic error) {
        return ctx.grammars().stream().filter(g -> g.getId().equals(error.grammarId())).findFirst().orElse(null);
    }

    /** Offset of an error: its start index, or computed from its line and column. */
    public static int errorOffset(String text, Diagnostic error) {
        if (error.startIndex() >= 0) {
            return Math.min(error.startIndex(), text.length());
        }
        int start = lineStart(text, Math.max(1, error.line()));
        return Math.min(text.length(), start + Math.max(0, error.column()));
    }

    private static String errorsNear(AssistContext ctx, int offset, int lines) {
        int line = lineOf(ctx.text(), offset);
        List<Diagnostic> near = ctx.errors().stream()
            .filter(e -> Math.abs(e.line() - line) <= lines)
            .limit(5)
            .toList();
        return describeErrors(ctx, near, "Parse errors near the cursor (the block may simply be unfinished):");
    }

    private static String errorsIn(AssistContext ctx, int start, int end) {
        List<Diagnostic> in = ctx.errors().stream()
            .filter(e -> {
                int o = errorOffset(ctx.text(), e);
                return o >= start && o <= end;
            })
            .limit(10)
            .toList();
        return describeErrors(ctx, in, "Parse errors in the passage:");
    }

    private static String describeErrors(AssistContext ctx, List<Diagnostic> errors, String header) {
        if (errors.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder(header).append('\n');
        for (Diagnostic e : errors) {
            b.append("- line ").append(e.line()).append(", column ").append(e.column())
                .append(": ").append(e.message()).append('\n');
        }
        return b.toString();
    }

    /** 1-based line of an offset. */
    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** Offset of the start of a 1-based line (the text length if there are fewer lines). */
    static int lineStart(String text, int line) {
        int offset = 0;
        for (int l = 1; l < line; l++) {
            int nl = text.indexOf('\n', offset);
            if (nl < 0) return text.length();
            offset = nl + 1;
        }
        return offset;
    }

    private static int lineStartBefore(String text, int offset) {
        return offset == 0 ? 0 : text.lastIndexOf('\n', offset - 1) + 1;
    }

    private static int lineEndAfter(String text, int offset) {
        int nl = text.indexOf('\n', offset);
        return nl < 0 ? text.length() : nl + 1;
    }
}
