package fr.cnrs.lacito.matchbox.corpus;

import java.util.Objects;

/**
 * Span of a {@link CorpusObject} (or of a {@link Field}) in its text.
 *
 * <p>An <em>empty</em> span has {@code stopIndex == startIndex - 1}: {@code startIndex} is then
 * the offset where a value would be inserted. Empty spans are only used for {@link Field}s: the
 * span of a corpus object is never empty.
 *
 * @param text       the text
 * @param startIndex offset of the first character (0-based)
 * @param stopIndex  offset of the last character (0-based, inclusive)
 * @param startLine  line of the first character (1-based)
 * @param endLine    line of the last character (1-based)
 */
public record TextCoordinate(TextId text, int startIndex, int stopIndex, int startLine, int endLine) {

    public TextCoordinate {
        Objects.requireNonNull(text, "text");
    }

    /** True for an empty span ({@code stopIndex == startIndex - 1}). */
    public boolean isEmpty() {
        return stopIndex == startIndex - 1;
    }

    /** Number of characters covered. */
    public int length() {
        return stopIndex - startIndex + 1;
    }

    /** True if {@code offset} lies within this span (both ends included); false for an empty span. */
    public boolean contains(int offset) {
        return offset >= startIndex && offset <= stopIndex;
    }

    /**
     * True if {@code other} lies within this span, in the same text. An empty span is inside this
     * span if its insertion offset is in {@code [startIndex, stopIndex + 1]}.
     */
    public boolean encloses(TextCoordinate other) {
        if (!Objects.equals(text, other.text)) {
            return false;
        }
        if (other.isEmpty()) {
            return other.startIndex >= startIndex && other.startIndex <= stopIndex + 1;
        }
        return other.startIndex >= startIndex && other.stopIndex <= stopIndex;
    }

    /** True if this span is a valid range (possibly empty) of a text of the given length. */
    public boolean isValidFor(int textLength) {
        if (startLine > endLine) {
            return false;
        }
        if (isEmpty()) {
            return startIndex >= 0 && startIndex <= textLength;
        }
        return startIndex >= 0
            && stopIndex >= startIndex
            && stopIndex < textLength;
    }
}
