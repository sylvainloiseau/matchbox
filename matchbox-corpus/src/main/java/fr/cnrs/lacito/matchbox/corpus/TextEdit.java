package fr.cnrs.lacito.matchbox.corpus;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Replacement of a range of a text.
 *
 * @param start            offset of the first replaced character
 * @param end              offset after the last replaced character ({@code start} for an insertion)
 * @param expectedOriginal the text expected in the range, checked before applying
 * @param replacement      the new text
 */
public record TextEdit(int start, int end, String expectedOriginal, String replacement) {

    public TextEdit {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range " + start + ".." + end);
        }
        Objects.requireNonNull(expectedOriginal, "expectedOriginal");
        Objects.requireNonNull(replacement, "replacement");
        if (expectedOriginal.length() != end - start) {
            throw new IllegalArgumentException("Expected text does not match the range " + start + ".." + end);
        }
    }

    public static TextEdit insert(int offset, String text) {
        return new TextEdit(offset, offset, "", text);
    }

    /** Replaces {@code [start, end)} of {@code text}. */
    public static TextEdit replace(String text, int start, int end, String replacement) {
        return new TextEdit(start, end, text.substring(start, end), replacement);
    }

    /**
     * Replaces the text of a span of {@code content}; the span's stop index is inclusive, and an
     * empty span gives an insertion at its start.
     */
    public static TextEdit replace(String content, TextCoordinate span, String replacement) {
        int start = span.startIndex();
        int end = span.isEmpty() ? start : span.stopIndex() + 1;
        return replace(content, start, end, replacement);
    }

    /** The edit that undoes this one, once this one is applied. */
    public TextEdit inverse() {
        return new TextEdit(start, start + replacement.length(), replacement, expectedOriginal);
    }

    /** Change of length of the text when this edit is applied. */
    public int delta() {
        return replacement.length() - (end - start);
    }

    /** True if the range of {@code text} still holds the expected original. */
    public boolean matches(String text) {
        return end <= text.length() && text.regionMatches(start, expectedOriginal, 0, expectedOriginal.length());
    }

    public TextEdit shifted(int delta) {
        return new TextEdit(start + delta, end + delta, expectedOriginal, replacement);
    }

    public String apply(String text) {
        if (!matches(text)) {
            throw new IllegalStateException("The text changed at " + start + ".." + end);
        }
        return text.substring(0, start) + replacement + text.substring(end);
    }

    /** Applies non-overlapping edits (in any order) to {@code text}. */
    public static String applyAll(String text, List<TextEdit> edits) {
        List<TextEdit> sorted = edits.stream().sorted(Comparator.comparingInt(TextEdit::start).reversed()).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).end() > sorted.get(i - 1).start()) {
                throw new IllegalArgumentException("Overlapping edits");
            }
        }
        String result = text;
        for (TextEdit e : sorted) {
            result = e.apply(result);
        }
        return result;
    }
}
