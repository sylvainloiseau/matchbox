package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.util.ArrayList;
import java.util.List;

/** The spans of the characters shown as "invisible": tabs, and spaces and tabs at the end of a line. */
public final class Invisibles {

    /** {@code [start, end)}. */
    public record Span(int start, int end) {}

    private Invisibles() {
    }

    public static List<Span> spans(String text) {
        List<Span> spans = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                int j = i;
                while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) j++;
                boolean trailing = j == text.length() || text.charAt(j) == '\n' || text.charAt(j) == '\r';
                if (trailing) {
                    spans.add(new Span(i, j));
                } else {
                    for (int k = i; k < j; k++) {
                        if (text.charAt(k) == '\t') spans.add(new Span(k, k + 1));
                    }
                }
                i = j;
            } else {
                i++;
            }
        }
        return spans;
    }
}
