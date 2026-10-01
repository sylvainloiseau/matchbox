package fr.cnrs.lacito.matchbox.corpus;

import java.nio.file.Path;

/**
 * Span of a {@link CorpusObject} in its source file.
 *
 * @param path       source file
 * @param startIndex offset of the first character (0-based)
 * @param stopIndex  offset of the last character (0-based, inclusive)
 * @param startLine  line of the first character (1-based)
 * @param endLine    line of the last character (1-based)
 */
public record TextCoordinate(Path path, int startIndex, int stopIndex, int startLine, int endLine) {

    /** True if {@code offset} lies within this span (both ends included). */
    public boolean contains(int offset) {
        return offset >= startIndex && offset <= stopIndex;
    }

    /** True if {@code other} lies within this span, in the same file. */
    public boolean encloses(TextCoordinate other) {
        return java.util.Objects.equals(path, other.path)
            && other.startIndex >= startIndex
            && other.stopIndex <= stopIndex;
    }

    /** True if this span is a valid range of a text of the given length. */
    public boolean isValidFor(int textLength) {
        return startIndex >= 0
            && stopIndex >= startIndex
            && stopIndex < textLength
            && startLine <= endLine;
    }
}
