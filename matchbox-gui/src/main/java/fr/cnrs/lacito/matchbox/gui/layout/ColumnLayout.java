package fr.cnrs.lacito.matchbox.gui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which panes of a split are shown and how much room each takes. Pure and immutable.
 *
 * <p>Each pane has a size, a fraction of the whole when every pane is shown (the sizes sum to 1).
 * A hidden pane keeps its size, so it comes back as it was; the shown panes share the room in
 * proportion to their sizes. The last shown pane cannot be hidden by {@link #withShown}.
 *
 * @param shown one flag per pane, in order
 * @param sizes one size per pane, in order, each at least {@link #MIN_SIZE}, summing to 1
 */
public record ColumnLayout(List<Boolean> shown, List<Double> sizes) {

    /** The smallest size a pane can have (as a fraction of the whole). */
    public static final double MIN_SIZE = 0.02;

    public ColumnLayout {
        Objects.requireNonNull(shown, "shown");
        Objects.requireNonNull(sizes, "sizes");
        if (shown.size() != sizes.size() || shown.isEmpty()) {
            throw new IllegalArgumentException("one flag and one size per pane: " + shown + " " + sizes);
        }
        shown = List.copyOf(shown);
        sizes = normalised(sizes);
    }

    /** Every pane shown, with these sizes (normalised). */
    public static ColumnLayout of(double... sizes) {
        List<Boolean> shown = new ArrayList<>();
        List<Double> list = new ArrayList<>();
        for (double s : sizes) {
            shown.add(true);
            list.add(s);
        }
        return new ColumnLayout(shown, list);
    }

    public int count() {
        return shown.size();
    }

    public boolean isShown(int pane) {
        return shown.get(pane);
    }

    /** The panes shown, in order. */
    public List<Integer> shownPanes() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i)) result.add(i);
        }
        return result;
    }

    /** True if the pane is shown and is not the last one shown. */
    public boolean canHide(int pane) {
        return shown.get(pane) && shownPanes().size() > 1;
    }

    /** The pane shown or hidden; unchanged if that would hide the last shown pane. */
    public ColumnLayout withShown(int pane, boolean show) {
        if (shown.get(pane) == show) return this;
        if (!show && !canHide(pane)) return this;
        List<Boolean> flags = new ArrayList<>(shown);
        flags.set(pane, show);
        return new ColumnLayout(flags, sizes);
    }

    /** Only these panes shown (at least one, else unchanged). */
    public ColumnLayout withOnly(List<Integer> panes) {
        if (panes.isEmpty()) return this;
        List<Boolean> flags = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) flags.add(panes.contains(i));
        return new ColumnLayout(flags, sizes);
    }

    /** Same panes shown, other sizes. */
    public ColumnLayout withSizes(List<Double> newSizes) {
        return new ColumnLayout(shown, newSizes);
    }

    /**
     * The divider positions of a split showing the shown panes (one fewer than them), from 0 to 1:
     * each shown pane takes its share of the sizes of the shown panes.
     */
    public double[] dividerPositions() {
        List<Integer> panes = shownPanes();
        if (panes.size() < 2) return new double[0];
        double total = 0;
        for (int p : panes) total += sizes.get(p);
        double[] positions = new double[panes.size() - 1];
        double at = 0;
        for (int i = 0; i < positions.length; i++) {
            at += sizes.get(panes.get(i));
            positions[i] = at / total;
        }
        return positions;
    }

    /**
     * The divider positions after the user moved them: the shown panes share the room they had
     * (their total size is kept) in the new proportions; the hidden panes keep their sizes.
     * Unchanged if the number of positions does not match.
     */
    public ColumnLayout dragged(double[] positions) {
        List<Integer> panes = shownPanes();
        if (positions.length != panes.size() - 1 || positions.length == 0) return this;
        double total = 0;
        for (int p : panes) total += sizes.get(p);
        List<Double> newSizes = new ArrayList<>(sizes);
        double previous = 0;
        for (int i = 0; i < panes.size(); i++) {
            double next = i < positions.length ? clamp(positions[i]) : 1;
            next = Math.max(next, previous);
            newSizes.set(panes.get(i), Math.max(MIN_SIZE, (next - previous) * total));
            previous = next;
        }
        return new ColumnLayout(shown, newSizes);
    }

    /**
     * A double-click on divider {@code d} (between the shown panes {@code d} and {@code d+1}):
     * shows a pane hidden between them; else, on the first (last) divider, a pane hidden before
     * (after) all the shown ones; else hides the narrower neighbour if it is not the last shown.
     */
    public ColumnLayout dividerDoubleClicked(int d) {
        List<Integer> panes = shownPanes();
        if (d < 0 || d >= panes.size() - 1) return this;
        int left = panes.get(d);
        int right = panes.get(d + 1);
        for (int i = left + 1; i < right; i++) {
            if (!shown.get(i)) return withShown(i, true);
        }
        if (d == 0) {
            for (int i = left - 1; i >= 0; i--) {
                if (!shown.get(i)) return withShown(i, true);
            }
        }
        if (d == panes.size() - 2) {
            for (int i = right + 1; i < shown.size(); i++) {
                if (!shown.get(i)) return withShown(i, true);
            }
        }
        int narrower = sizes.get(left) <= sizes.get(right) ? left : right;
        return withShown(narrower, false);
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    /** Sizes at least {@link #MIN_SIZE}, summing to 1; invalid values become {@link #MIN_SIZE}. */
    private static List<Double> normalised(List<Double> sizes) {
        List<Double> result = new ArrayList<>();
        double total = 0;
        for (Double s : sizes) {
            double v = s == null || s.isNaN() || s.isInfinite() || s < MIN_SIZE ? MIN_SIZE : s;
            result.add(v);
            total += v;
        }
        // rounding: keep the values exact when they already sum to 1
        if (Math.abs(total - 1) > 1e-9) {
            for (int i = 0; i < result.size(); i++) result.set(i, Math.max(MIN_SIZE, result.get(i) / total));
        }
        return List.copyOf(result);
    }
}
