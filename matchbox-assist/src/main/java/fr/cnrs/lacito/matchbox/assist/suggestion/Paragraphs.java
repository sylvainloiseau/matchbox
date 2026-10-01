package fr.cnrs.lacito.matchbox.assist.suggestion;

/** Paragraphs of a text: runs of lines separated by blank lines. */
public final class Paragraphs {

    private Paragraphs() {}

    /** Offset of the start of the paragraph containing {@code offset} (after the previous blank line). */
    public static int start(String text, int offset) {
        int i = Math.min(offset, text.length());
        while (i > 0) {
            int lineStart = text.lastIndexOf('\n', i - 1);
            if (lineStart < 0) return 0;
            int prevLineStart = text.lastIndexOf('\n', lineStart - 1) + 1;
            if (text.substring(prevLineStart, lineStart).isBlank()) {
                return lineStart + 1;
            }
            i = lineStart;
        }
        return 0;
    }

    /** Offset of the end of the paragraph containing {@code offset} (the start of the next blank line). */
    public static int end(String text, int offset) {
        int i = Math.max(0, Math.min(offset, text.length()));
        int lineEnd = text.indexOf('\n', i);
        while (lineEnd >= 0) {
            int nextEnd = text.indexOf('\n', lineEnd + 1);
            String nextLine = text.substring(lineEnd + 1, nextEnd < 0 ? text.length() : nextEnd);
            if (nextLine.isBlank()) {
                return lineEnd + 1;
            }
            lineEnd = nextEnd;
        }
        return text.length();
    }

    /**
     * Splits {@code text[start, end)} into consecutive chunks of at most about {@code maxLength}
     * characters, cut at paragraph boundaries when possible.
     */
    public static java.util.List<int[]> chunks(String text, int start, int end, int maxLength) {
        java.util.List<int[]> chunks = new java.util.ArrayList<>();
        int from = start;
        while (from < end) {
            int to = Math.min(end, from + maxLength);
            if (to < end) {
                int cut = start(text, to);
                if (cut > from) to = cut;
            }
            chunks.add(new int[] {from, to});
            from = to;
        }
        return chunks;
    }
}
