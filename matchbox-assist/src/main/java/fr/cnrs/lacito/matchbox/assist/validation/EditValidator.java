package fr.cnrs.lacito.matchbox.assist.validation;

import fr.cnrs.lacito.matchbox.assist.suggestion.Paragraphs;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.grammarapi.TextParser;
import fr.cnrs.lacito.matchbox.corpus.MatchConflict;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Checks proposed edits with the grammars of the file: the edits are applied to a copy of the
 * text, the copy is parsed, and its errors are compared with those of the current text.
 *
 * <p>An edit is rejected if it increases the number of errors, or if it adds errors in the edited
 * range or in the rest of its paragraph. Parsing runs on a dedicated executor, so that validation
 * does not delay the parses of the project.
 *
 * <p>For large texts, only the paragraphs around the edits are parsed (paragraphs are separated by
 * blank lines and assumed independent).
 */
public final class EditValidator implements AutoCloseable {

    /** Above this length, only the paragraphs around the edits are parsed. */
    static final int LARGE_TEXT = 200_000;

    private final ExecutorService executor;
    private Baseline baseline;

    /** Errors of the last parsed original text, reused for successive checks of the same text. */
    private record Baseline(TextId file, String text, List<String> grammarIds, List<Diagnostic> errors) {}

    public EditValidator() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "matchbox-assist-validate");
            t.setDaemon(true);
            return t;
        });
    }

    /** Result of a check. {@code errorsBefore - errorsAfter} errors are fixed by the edits. */
    public record Result(boolean accepted, int errorsBefore, int errorsAfter, List<Diagnostic> newErrors) {
        public int fixedErrors() {
            return Math.max(0, errorsBefore - errorsAfter);
        }
    }

    public CompletableFuture<Result> checkAsync(TextId file, String text, List<TextEdit> edits, List<GrammarPlugin> grammars) {
        return CompletableFuture.supplyAsync(() -> check(file, text, edits, grammars), executor);
    }

    /** Synchronous check, on the calling thread. */
    public Result check(TextId file, String text, List<TextEdit> edits, List<GrammarPlugin> grammars) {
        if (edits.isEmpty() || grammars.isEmpty()) {
            return new Result(true, 0, 0, List.of());
        }
        int minStart = edits.stream().mapToInt(TextEdit::start).min().orElseThrow();
        int maxEnd = edits.stream().mapToInt(TextEdit::end).max().orElseThrow();
        int delta = edits.stream().mapToInt(TextEdit::delta).sum();

        // the part of the text that is parsed
        int windowStart = 0;
        int windowEnd = text.length();
        if (text.length() > LARGE_TEXT) {
            windowStart = paragraphStart(text, minStart);
            windowEnd = paragraphEnd(text, maxEnd);
        }
        String original = text.substring(windowStart, windowEnd);
        int shift = -windowStart;
        List<TextEdit> local = edits.stream().map(e -> e.shifted(shift)).toList();
        String edited = TextEdit.applyAll(original, local);

        List<Diagnostic> before = windowStart == 0 && windowEnd == text.length()
            ? baselineErrors(file, text, grammars)
            : parse(file, original, grammars);
        List<Diagnostic> after = parse(file, edited, grammars);

        // the edited range and the rest of its paragraph, in the edited text
        int rangeStart = minStart - windowStart;
        int rangeEndBefore = maxEnd - windowStart;
        int editedRangeEnd = paragraphEnd(edited, rangeEndBefore + delta);
        List<Diagnostic> unmatched = new ArrayList<>(before);
        List<Diagnostic> newErrors = new ArrayList<>();
        for (Diagnostic e : after) {
            if (!inRange(e, rangeStart, editedRangeEnd)) {
                continue;
            }
            // an error that was already there (same grammar and kind, at the shifted position)
            Diagnostic previous = unmatched.stream()
                .filter(b -> b.grammarId().equals(e.grammarId()) && kind(b).equals(kind(e))
                    && mapped(b.startIndex(), rangeEndBefore, delta) == e.startIndex())
                .findFirst().orElse(null);
            if (previous != null) {
                unmatched.remove(previous);
            } else {
                newErrors.add(e);
            }
        }
        boolean accepted = after.size() <= before.size() && newErrors.isEmpty();
        return new Result(accepted, before.size(), after.size(), newErrors);
    }

    private synchronized List<Diagnostic> baselineErrors(TextId file, String text, List<GrammarPlugin> grammars) {
        List<String> ids = grammars.stream().map(GrammarPlugin::getId).toList();
        Baseline b = baseline;
        if (b != null && Objects.equals(b.file(), file) && b.grammarIds().equals(ids) && b.text().equals(text)) {
            return b.errors();
        }
        List<Diagnostic> errors = parse(file, text, grammars);
        baseline = new Baseline(file, text, ids, errors);
        return errors;
    }

    /** Offset of an original error in the edited text: errors at or after the edits are shifted. */
    private static int mapped(int offset, int editsEnd, int delta) {
        return offset >= editsEnd ? offset + delta : offset;
    }

    /** Kind of error, from the start of the ANTLR message ("missing", "extraneous input"…). */
    static String kind(Diagnostic e) {
        if (e.isWarning()) {
            // the message names lines, which move with the edits
            return "not kept";
        }
        String m = e.message() == null ? "" : e.message();
        for (String k : List.of("missing", "extraneous input", "mismatched input", "no viable alternative",
                "token recognition error")) {
            if (m.startsWith(k)) return k;
        }
        return m;
    }

    private static boolean inRange(Diagnostic e, int start, int end) {
        // errors at the end of the range (e.g. at end of file) count as in the range
        return e.startIndex() >= start && e.startIndex() <= end;
    }

    /**
     * Problems of a text that an edit must not add: the errors of the grammars, and the warnings
     * of the objects not kept because an earlier grammar reads the same characters (an edit making
     * an IGT disappear is a loss). The other overlap warnings are not counted.
     */
    static List<Diagnostic> parse(TextId file, String text, List<GrammarPlugin> grammars) {
        Text parsed = TextParser.parse(file, text, grammars);
        Set<String> dropped = new HashSet<>();
        for (MatchConflict c : parsed.conflicts()) {
            if (c.isDropped()) {
                dropped.add(c.grammarId() + "@" + c.object().getTextCoordinate().startIndex());
            }
        }
        List<Diagnostic> problems = new ArrayList<>();
        for (Diagnostic d : parsed.diagnostics()) {
            if (d.isError() || dropped.contains(d.grammarId() + "@" + d.startIndex())) {
                problems.add(d);
            }
        }
        return problems;
    }

    static int paragraphStart(String text, int offset) {
        return Paragraphs.start(text, offset);
    }

    static int paragraphEnd(String text, int offset) {
        return Paragraphs.end(text, offset);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
