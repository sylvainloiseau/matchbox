package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A problem reported while parsing a text: syntax error, invalid object, internal grammar error
 * ({@link Severity#ERROR}), or an object not kept because another grammar already reads the same
 * characters ({@link Severity#WARNING}, see {@link MatchRole}).
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
 * @param severity   error (syntax, invalid object, grammar failure) or warning (overlapping readings)
 */
public record Diagnostic(TextId text, String grammarId, int line, int column, String message,
                         int startIndex, int stopIndex, List<String> expected, Severity severity) {

    /** How serious a diagnostic is. */
    public enum Severity {
        /** The text does not follow the grammar, or the grammar failed. */
        ERROR,
        /** The text parses, but something was not kept (e.g. an overlapping reading). */
        WARNING
    }

    public Diagnostic {
        Objects.requireNonNull(text, "text");
        expected = List.copyOf(expected);
        severity = severity == null ? Severity.ERROR : severity;
    }

    /** An error (the constructor used by grammars). */
    public Diagnostic(TextId text, String grammarId, int line, int column, String message,
                      int startIndex, int stopIndex, List<String> expected) {
        this(text, grammarId, line, column, message, startIndex, stopIndex, expected, Severity.ERROR);
    }

    /** An error without offsets. */
    public static Diagnostic at(TextId text, String grammarId, int line, int column, String message) {
        return new Diagnostic(text, grammarId, line, column, message, -1, -1, List.of());
    }

    /** A warning on a range of the text ({@code stopIndex} inclusive). */
    public static Diagnostic warning(TextId text, String grammarId, int line, int column, String message,
                                     int startIndex, int stopIndex) {
        return new Diagnostic(text, grammarId, line, column, message, startIndex, stopIndex, List.of(), Severity.WARNING);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
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
