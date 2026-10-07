package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import java.util.ArrayList;
import java.util.List;

/** Line numbers (1-based) of offsets of a text; line endings \n, \r\n and \r. */
final class LineIndex {

    private final int[] starts;

    LineIndex(String text) {
        List<Integer> s = new ArrayList<>();
        s.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                s.add(i + 1);
            }
        }
        starts = s.stream().mapToInt(Integer::intValue).toArray();
    }

    int lineOf(int offset) {
        int lo = 0;
        int hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= offset) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }

    /** The line ending used by a text: "\r\n", "\r" or "\n" (default). */
    static String lineEnding(String text) {
        if (text.contains("\r\n")) return "\r\n";
        if (text.indexOf('\r') >= 0 && text.indexOf('\n') < 0) return "\r";
        return "\n";
    }

    /** Offset of the end of the line containing {@code offset}, line ending excluded. */
    static int lineEnd(String text, int offset) {
        int i = offset;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
        return i;
    }

    /** Offset of the start of the line containing {@code offset}. */
    static int lineStart(String text, int offset) {
        int i = Math.min(offset, text.length());
        while (i > 0 && text.charAt(i - 1) != '\n' && text.charAt(i - 1) != '\r') i--;
        return i;
    }

    /** Offset after the line ending of the line containing {@code offset} (or the text length). */
    static int nextLineStart(String text, int offset) {
        int i = lineEnd(text, offset);
        if (i < text.length() && text.charAt(i) == '\r') i++;
        if (i < text.length() && text.charAt(i) == '\n') i++;
        return i;
    }
}
