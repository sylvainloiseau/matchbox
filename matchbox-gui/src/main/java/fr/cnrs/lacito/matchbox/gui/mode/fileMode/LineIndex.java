package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.util.Arrays;

/** The lines of a text ("\n" separated): line of an offset, start and end of a line. Lines count from 0. */
public final class LineIndex {

    private final String text;
    private final int[] starts;

    public LineIndex(String text) {
        this.text = text;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        starts = new int[count];
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts[line++] = i + 1;
        }
    }

    public int lineCount() {
        return starts.length;
    }

    /** The line of an offset (an offset past the end is on the last line). */
    public int lineOf(int offset) {
        int i = Arrays.binarySearch(starts, Math.max(0, Math.min(offset, text.length())));
        return i >= 0 ? i : -i - 2;
    }

    public int start(int line) {
        return starts[line];
    }

    /** Offset of the end of a line, before its "\n". */
    public int end(int line) {
        return line + 1 < starts.length ? starts[line + 1] - 1 : text.length();
    }

    public String line(int line) {
        return text.substring(start(line), end(line));
    }

    public String text() {
        return text;
    }
}
