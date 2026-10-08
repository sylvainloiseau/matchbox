package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import java.util.ArrayList;
import java.util.List;

/**
 * Lines of a text around a span (a tag): {@code before} lines above the line of the span's start,
 * {@code after} lines below the line of its end, cut at the start and end of the text.
 *
 * @param lines the lines, in order
 */
public record ContextExcerpt(List<Line> lines) {

    /**
     * A line of the text.
     *
     * @param number 1-based line number
     * @param start  offset of its first character in the text
     * @param text   its characters, without the line break ({@code \n}, {@code \r\n} or {@code \r})
     */
    public record Line(int number, int start, String text) {
        public int end() {
            return start + text.length();
        }
    }

    public ContextExcerpt {
        lines = List.copyOf(lines);
    }

    /**
     * @param start first offset of the span
     * @param end   end of the span, exclusive
     */
    public static ContextExcerpt of(String content, int start, int end, int before, int after) {
        List<Line> all = lines(content);
        int first = lineIndex(all, start);
        int last = lineIndex(all, Math.max(start, end - 1));
        int from = Math.max(0, first - Math.max(0, before));
        int to = Math.min(all.size() - 1, last + Math.max(0, after));
        return new ContextExcerpt(all.subList(from, to + 1));
    }

    /** The lines of a text (a text without characters has one empty line). */
    static List<Line> lines(String content) {
        List<Line> lines = new ArrayList<>();
        int lineStart = 0;
        int number = 1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(new Line(number++, lineStart, content.substring(lineStart, i)));
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                lineStart = i + 1;
            }
        }
        if (lineStart < content.length() || lines.isEmpty()) {
            lines.add(new Line(number, lineStart, content.substring(lineStart)));
        }
        return lines;
    }

    private static int lineIndex(List<Line> lines, int offset) {
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            // the line break belongs to its line
            int next = i + 1 < lines.size() ? lines.get(i + 1).start() : Integer.MAX_VALUE;
            if (offset >= l.start() && offset < next) return i;
        }
        return lines.size() - 1;
    }
}
