package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A problem found in a text, reported by a grammar or by an object it produced.
 *
 * <p>Two independent axes describe it: its {@linkplain Severity severity} (how serious it is) and
 * its {@linkplain Category category} (what kind of problem it is, which decides where the
 * application lists it: syntax and internal errors with the parsing errors, semantic issues apart).
 *
 * <p>The 8- and 9-argument constructors, {@link #at} and {@link #warning(TextId, String, int, int, String, int, int)}
 * give a {@link Category#SYNTAX} diagnostic without code nor fix, as before categories existed;
 * use {@link #semantic} for a problem of content and {@link #internal} for a failure of the grammar.
 *
 * @param text       the text
 * @param grammarId  id of the grammar reporting it
 * @param line       line of the problem (1-based), 0 if unknown
 * @param column     position in the line (0-based)
 * @param message    human-readable description
 * @param startIndex offset of the first offending character (0-based), -1 if unknown; may be the
 *                   length of the text for an error at end of file
 * @param stopIndex  offset of the last offending character (inclusive), -1 if unknown;
 *                   {@code startIndex - 1} for an empty range (e.g. at end of file)
 * @param expected   tokens expected at this position, as displayed by ANTLR: the literal between
 *                   quotes when the token has one (e.g. {@code "'<'"}), its symbolic name
 *                   otherwise (e.g. {@code "IMB_LINE"}), {@code "<EOF>"}
 * @param severity   how serious it is
 * @param category   what kind of problem it is
 * @param code       stable identifier of the kind of problem (e.g. {@code "mediaref.range-order"}),
 *                   empty if none
 * @param fixes      edits that would correct it, relative to the parsed content; may be empty
 */
public record Diagnostic(TextId text, String grammarId, int line, int column, String message,
                         int startIndex, int stopIndex, List<String> expected, Severity severity,
                         Category category, String code, List<Fix> fixes) {

    /** How serious a diagnostic is. */
    public enum Severity {
        /** The text cannot be read as intended (or the grammar failed). */
        ERROR,
        /** The text is read, but something is doubtful or was not kept. */
        WARNING
    }

    /** What kind of problem a diagnostic is. */
    public enum Category {
        /** The text does not follow the notation of the grammar. */
        SYNTAX,
        /** The text follows the notation, but what it says is wrong or doubtful. */
        SEMANTIC,
        /** A reading was not kept, or crosses another one (see {@link MatchRole}). */
        OVERLAP,
        /** The grammar failed, or returned an invalid object. */
        INTERNAL
    }

    /**
     * Edits correcting a diagnostic, applied as one undoable change.
     *
     * @param label       short text of the command (e.g. "Swap start and end")
     * @param edits       edits relative to the content the diagnostic was found in
     * @param description label of the change in the undo journal; the label if blank
     */
    public record Fix(String label, List<TextEdit> edits, String description) {

        public Fix {
            Objects.requireNonNull(label, "label");
            edits = List.copyOf(edits);
            description = description == null || description.isBlank() ? label : description;
        }

        public Fix(String label, List<TextEdit> edits) {
            this(label, edits, label);
        }
    }

    public Diagnostic {
        Objects.requireNonNull(text, "text");
        expected = expected == null ? List.of() : List.copyOf(expected);
        severity = severity == null ? Severity.ERROR : severity;
        category = category == null ? Category.SYNTAX : category;
        code = code == null ? "" : code;
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }

    /** A syntax diagnostic without code nor fix (the constructor used before categories). */
    public Diagnostic(TextId text, String grammarId, int line, int column, String message,
                      int startIndex, int stopIndex, List<String> expected, Severity severity) {
        this(text, grammarId, line, column, message, startIndex, stopIndex, expected, severity,
            Category.SYNTAX, "", List.of());
    }

    /** A syntax error (the constructor used by grammars). */
    public Diagnostic(TextId text, String grammarId, int line, int column, String message,
                      int startIndex, int stopIndex, List<String> expected) {
        this(text, grammarId, line, column, message, startIndex, stopIndex, expected, Severity.ERROR);
    }

    /** A syntax error without offsets. */
    public static Diagnostic at(TextId text, String grammarId, int line, int column, String message) {
        return new Diagnostic(text, grammarId, line, column, message, -1, -1, List.of());
    }

    /** A syntax warning on a range of the text ({@code stopIndex} inclusive). */
    public static Diagnostic warning(TextId text, String grammarId, int line, int column, String message,
                                     int startIndex, int stopIndex) {
        return new Diagnostic(text, grammarId, line, column, message, startIndex, stopIndex, List.of(), Severity.WARNING);
    }

    /**
     * A problem of content: the text follows the notation, but what it says is wrong or doubtful
     * (e.g. a time range ending before it starts). Listed apart from the parsing errors.
     */
    public static Diagnostic semantic(TextId text, String grammarId, int line, int column, String message,
                                      int startIndex, int stopIndex, Severity severity) {
        return new Diagnostic(text, grammarId, line, column, message, startIndex, stopIndex, List.of(),
            severity, Category.SEMANTIC, "", List.of());
    }

    /** A failure of the grammar (exception, invalid object), without offsets. */
    public static Diagnostic internal(TextId text, String grammarId, int line, String message) {
        return new Diagnostic(text, grammarId, line, 0, message, -1, -1, List.of(), Severity.ERROR,
            Category.INTERNAL, "internal", List.of());
    }

    /** The same diagnostic with another category. */
    public Diagnostic withCategory(Category newCategory) {
        return new Diagnostic(text, grammarId, line, column, message, startIndex, stopIndex, expected, severity,
            newCategory, code, fixes);
    }

    /** The same diagnostic with a code (see {@link #code()}). */
    public Diagnostic withCode(String newCode) {
        return new Diagnostic(text, grammarId, line, column, message, startIndex, stopIndex, expected, severity,
            category, newCode, fixes);
    }

    /** The same diagnostic with fixes. */
    public Diagnostic withFixes(List<Fix> newFixes) {
        return new Diagnostic(text, grammarId, line, column, message, startIndex, stopIndex, expected, severity,
            category, code, newFixes);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    public boolean isSemantic() {
        return category == Category.SEMANTIC;
    }

    /**
     * Span of the offending input, if known and not empty. The end line is the start line:
     * offending tokens are assumed not to span several lines.
     */
    public Optional<TextCoordinate> span() {
        if (startIndex < 0 || stopIndex < startIndex) {
            return Optional.empty();
        }
        return Optional.of(new TextCoordinate(text, startIndex, stopIndex, line, line));
    }

    @Override
    public String toString() {
        return "line " + line + ":" + column + " " + message;
    }
}
