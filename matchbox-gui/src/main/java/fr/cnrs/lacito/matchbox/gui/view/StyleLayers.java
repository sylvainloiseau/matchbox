package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Composes the styles of nested and overlapping spans: on each run of characters, the style of a
 * layer added later (a child, a tag) overrides the properties it sets, and the others come from the
 * layers below (its parents). Pure: the editor turns the runs into style spans.
 */
public final class StyleLayers {

    /** A run of {@code length} characters with one composed style. */
    public record Run(int length, DisplayStyle style) {}

    private record Layer(int start, int end, int order, DisplayStyle style) {}

    private final List<Layer> layers = new ArrayList<>();

    /**
     * Adds a layer above the previous ones.
     *
     * @param start first character
     * @param end   character after the last one (exclusive)
     */
    public void add(int start, int end, DisplayStyle style) {
        if (end > start && style != null && !style.isEmpty()) {
            layers.add(new Layer(start, end, layers.size(), style));
        }
    }

    /**
     * Runs covering {@code [0, length)}; characters of no layer have {@link DisplayStyle#EMPTY};
     * layers are clipped to the length.
     */
    public List<Run> runs(int length) {
        List<Run> runs = new ArrayList<>();
        if (length <= 0) return runs;
        // events: a layer starts or ends at a position
        List<int[]> events = new ArrayList<>();
        for (Layer l : layers) {
            int s = Math.min(Math.max(0, l.start), length);
            int e = Math.min(l.end, length);
            if (e <= s) continue;
            events.add(new int[] {s, l.order, 1});
            events.add(new int[] {e, l.order, 0});
        }
        events.sort(Comparator.<int[]>comparingInt(a -> a[0]).thenComparingInt(a -> a[2]));
        Map<Integer, DisplayStyle> active = new TreeMap<>();
        int position = 0;
        int i = 0;
        while (position < length) {
            while (i < events.size() && events.get(i)[0] == position) {
                int[] ev = events.get(i++);
                if (ev[2] == 1) active.put(ev[1], layers.get(ev[1]).style);
                else active.remove(ev[1]);
            }
            int next = i < events.size() ? events.get(i)[0] : length;
            DisplayStyle composed = DisplayStyle.EMPTY;
            for (DisplayStyle s : active.values()) {
                composed = s.over(composed);
            }
            append(runs, next - position, composed);
            position = next;
        }
        return runs;
    }

    /** Merges consecutive runs of the same style. */
    private static void append(List<Run> runs, int length, DisplayStyle style) {
        if (length <= 0) return;
        if (!runs.isEmpty() && runs.get(runs.size() - 1).style.equals(style)) {
            Run last = runs.remove(runs.size() - 1);
            runs.add(new Run(last.length + length, style));
        } else {
            runs.add(new Run(length, style));
        }
    }
}
