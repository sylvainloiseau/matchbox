package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the margin of the editor shows on each line (counted from 0): the media segments starting
 * there (▶), the text actions of plugins (✚), the parse errors (●) and the warnings (◆, e.g. a
 * reading not kept because another grammar reads the same text); the width of the line numbers. Pure: computed from the parsed text, without controls.
 */
public final class GutterLayout {

    /**
     * The markers of a line.
     *
     * @param errors   messages of the errors
     * @param warnings the warnings (their spans are outlined while the marker is hovered)
     */
    public record Markers(List<MediaSegment> segments, List<TextAction> actions, List<String> errors,
                          List<Diagnostic> warnings) {

        public Markers(List<MediaSegment> segments, List<TextAction> actions, List<String> errors) {
            this(segments, actions, errors, List.of());
        }

        public boolean isEmpty() {
            return segments.isEmpty() && actions.isEmpty() && errors.isEmpty() && warnings.isEmpty();
        }

        /** True if the line has warnings but no error (◆; an error wins: ●). */
        public boolean warningOnly() {
            return errors.isEmpty() && !warnings.isEmpty();
        }

        /** Messages of the errors, then of the warnings (tooltips). */
        public List<String> messages() {
            List<String> all = new ArrayList<>(errors);
            warnings.forEach(w -> all.add(w.message()));
            return all;
        }
    }

    private GutterLayout() {
    }

    /** Number of digits of the last line number (at least 2, so that the margin rarely changes width). */
    public static int digits(int lineCount) {
        return Math.max(2, Integer.toString(Math.max(1, lineCount)).length());
    }

    /** The lines with markers. */
    public static Map<Integer, Markers> markers(LineIndex index, List<MediaSegment> segments, List<TextAction> actions,
                                                List<Diagnostic> diagnostics) {
        Map<Integer, List<MediaSegment>> s = new TreeMap<>();
        Map<Integer, List<TextAction>> a = new TreeMap<>();
        Map<Integer, List<String>> e = new TreeMap<>();
        Map<Integer, List<Diagnostic>> w = new TreeMap<>();
        for (MediaSegment segment : segments) {
            s.computeIfAbsent(index.lineOf(segment.getTextCoordinate().startIndex()), k -> new ArrayList<>()).add(segment);
        }
        for (TextAction action : actions) {
            a.computeIfAbsent(index.lineOf(action.offset()), k -> new ArrayList<>()).add(action);
        }
        for (Diagnostic d : diagnostics) {
            if (d.isWarning()) {
                w.computeIfAbsent(lineOf(index, d), k -> new ArrayList<>()).add(d);
            } else {
                e.computeIfAbsent(lineOf(index, d), k -> new ArrayList<>()).add(d.message());
            }
        }
        Map<Integer, Markers> markers = new TreeMap<>();
        for (Integer line : union(s, a, e, w)) {
            markers.put(line, new Markers(s.getOrDefault(line, List.of()), a.getOrDefault(line, List.of()),
                e.getOrDefault(line, List.of()), w.getOrDefault(line, List.of())));
        }
        return markers;
    }

    /** The line of a diagnostic: from its span, else from its (1-based) line number. */
    static int lineOf(LineIndex index, Diagnostic d) {
        if (d.startIndex() >= 0) return index.lineOf(d.startIndex());
        return Math.max(0, Math.min(index.lineCount() - 1, d.line() - 1));
    }

    private static List<Integer> union(Map<Integer, ?>... maps) {
        java.util.TreeSet<Integer> lines = new java.util.TreeSet<>();
        for (Map<Integer, ?> m : maps) lines.addAll(m.keySet());
        return List.copyOf(lines);
    }

    /** Vertical position (0–1) of a line's mark on the error ruler. */
    public static double rulerPosition(int line, int lineCount) {
        return (line + 0.5) / Math.max(1, lineCount);
    }
}
