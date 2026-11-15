package fr.cnrs.lacito.matchbox.gui.mode.history;

import fr.cnrs.lacito.matchbox.gui.layout.ColumnLayout;
import fr.cnrs.lacito.matchbox.gui.layout.PanelModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout of the Dictionary History mode: the table of entries beside a column holding the entry
 * above its commands. Pure and immutable. Panels: 0 entries, 1 entry, 2 commands; the last shown
 * panel cannot be hidden.
 *
 * @param shown   one flag per panel
 * @param widths  sizes of the entries and of the column (sum 1)
 * @param heights sizes of the entry and of the commands (sum 1)
 */
public record HistoryLayout(List<Boolean> shown, List<Double> widths, List<Double> heights) {

    public static final int ENTRIES = 0;
    public static final int ENTRY = 1;
    public static final int COMMANDS = 2;

    public HistoryLayout {
        shown = List.copyOf(shown);
        if (shown.size() != 3) throw new IllegalArgumentException("three panels: " + shown);
        widths = ColumnLayout.of(widths.stream().mapToDouble(Double::doubleValue).toArray()).sizes();
        heights = ColumnLayout.of(heights.stream().mapToDouble(Double::doubleValue).toArray()).sizes();
    }

    public boolean isShown(int panel) {
        return shown.get(panel);
    }

    private int shownCount() {
        return (int) shown.stream().filter(b -> b).count();
    }

    public boolean canHide(int panel) {
        return isShown(panel) && shownCount() > 1;
    }

    public HistoryLayout withShown(int panel, boolean show) {
        if (isShown(panel) == show || (!show && !canHide(panel))) return this;
        List<Boolean> s = new ArrayList<>(shown);
        s.set(panel, show);
        return new HistoryLayout(s, widths, heights);
    }

    public HistoryLayout withAll() {
        return new HistoryLayout(List.of(true, true, true), widths, heights);
    }

    public HistoryLayout withSizes(List<Double> newWidths, List<Double> newHeights) {
        return new HistoryLayout(shown, newWidths, newHeights);
    }

    /** The horizontal split: the entries, and the column of the entry and its commands. */
    public ColumnLayout outer() {
        return new ColumnLayout(List.of(isShown(ENTRIES), isShown(ENTRY) || isShown(COMMANDS)), widths);
    }

    /** The vertical split of the column. */
    public ColumnLayout inner() {
        return new ColumnLayout(List.of(isShown(ENTRY), isShown(COMMANDS)), heights);
    }

    /** After the user changed the horizontal split (drag, double-click). */
    public HistoryLayout withOuter(ColumnLayout outer) {
        boolean entries = outer.isShown(0);
        boolean entry = isShown(ENTRY);
        boolean commands = isShown(COMMANDS);
        if (!outer.isShown(1)) {
            entry = commands = false;
        } else if (!entry && !commands) {
            entry = true;
        }
        if (!entries && !entry && !commands) return this;
        return new HistoryLayout(List.of(entries, entry, commands), outer.sizes(), heights);
    }

    /** After the user changed the vertical split. */
    public HistoryLayout withInner(ColumnLayout inner) {
        if (!isShown(ENTRIES) && inner.shownPanes().isEmpty()) return this;
        return new HistoryLayout(List.of(isShown(ENTRIES), inner.isShown(0), inner.isShown(1)), widths, inner.sizes());
    }

    public static PanelModel<HistoryLayout> panels() {
        return new PanelModel<>() {
            @Override
            public boolean isShown(HistoryLayout layout, int panel) {
                return layout.isShown(panel);
            }

            @Override
            public boolean canHide(HistoryLayout layout, int panel) {
                return layout.canHide(panel);
            }

            @Override
            public HistoryLayout withShown(HistoryLayout layout, int panel, boolean show) {
                return layout.withShown(panel, show);
            }
        };
    }
}
