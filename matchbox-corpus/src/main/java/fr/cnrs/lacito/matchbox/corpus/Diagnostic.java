package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A problem reported while parsing a text: syntax error, invalid object, internal grammar error.
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
 */
public record Diagnostic(TextId text, String grammarId, int line, int column, String message,
                         int startIndex, int stopIndex, List<String> expected) {

    public Diagnostic {
        Objects.requireNonNull(text, "text");
        expected = List.copyOf(expected);
    }

    /** A diagnostic without offsets. */
    public static Diagnostic at(TextId text, String grammarId, int line, int column, String message) {
        return new Diagnostic(text, grammarId, line, column, message, -1, -1, List.of());
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
