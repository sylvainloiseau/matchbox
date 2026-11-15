package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.gui.layout.ColumnLayout;
import fr.cnrs.lacito.matchbox.gui.layout.PanelModel;
import fr.cnrs.lacito.matchbox.settings.FileModeSettingsData;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout of File Mode: which of its three panels are shown, how they are arranged and their sizes.
 * Pure and immutable; read from and written to {@link FileModeSettingsData}.
 *
 * <p>In {@link Arrangement#COLUMNS} the panels are three columns of one horizontal split
 * ({@link #outer()}). In {@link Arrangement#STACKED} the outer split has the file list and a
 * vertical split ({@link #inner()}) with the editor above the objects. The shown flags are the
 * same in both; the last shown panel cannot be hidden.
 *
 * @param arrangement    columns or stacked
 * @param shown          one flag per {@link Panel}, in order
 * @param columnSizes    sizes of the three columns (columns)
 * @param stackedWidths  sizes of the file list and of the stack (stacked)
 * @param stackedHeights sizes of the editor and of the objects in the stack (stacked)
 */
public record FileModeLayout(Arrangement arrangement, List<Boolean> shown, List<Double> columnSizes,
                             List<Double> stackedWidths, List<Double> stackedHeights) {

    /** The panels of File Mode, in order. */
    public enum Panel {
        FILES, EDITOR, OBJECTS
    }

    public enum Arrangement {
        /** Files | editor | objects. */
        COLUMNS,
        /** Files | editor over objects. */
        STACKED
    }

    /** Layouts chosen in one step (File Mode › Panels). */
    public enum Preset {
        ALL(Panel.FILES, Panel.EDITOR, Panel.OBJECTS),
        WRITING(Panel.EDITOR),
        REVIEWING(Panel.EDITOR, Panel.OBJECTS),
        BROWSING(Panel.FILES, Panel.OBJECTS);

        private final List<Panel> panels;

        Preset(Panel... panels) {
            this.panels = List.of(panels);
        }

        public List<Panel> panels() {
            return panels;
        }
    }

    public FileModeLayout {
        shown = List.copyOf(shown);
        if (shown.size() != 3) throw new IllegalArgumentException("three panels: " + shown);
        // the column layouts normalise the sizes
        columnSizes = ColumnLayout.of(toArray(columnSizes)).sizes();
        stackedWidths = ColumnLayout.of(toArray(stackedWidths)).sizes();
        stackedHeights = ColumnLayout.of(toArray(stackedHeights)).sizes();
    }

    public static FileModeLayout defaults() {
        return of(FileModeSettingsData.defaults());
    }

    /** The layout of validated settings. */
    public static FileModeLayout of(FileModeSettingsData d) {
        FileModeSettingsData v = d == null ? FileModeSettingsData.defaults() : d.validated(p -> {});
        return new FileModeLayout(
            FileModeSettingsData.STACKED.equals(v.arrangement()) ? Arrangement.STACKED : Arrangement.COLUMNS,
            List.of(v.showFiles(), v.showEditor(), v.showObjects()),
            v.columnWidths(), v.stackedWidths(), v.stackedHeights());
    }

    /** These settings with this layout (the other fields kept). */
    public FileModeSettingsData into(FileModeSettingsData d) {
        FileModeSettingsData base = d == null ? FileModeSettingsData.defaults() : d;
        return base.withArrangement(arrangement == Arrangement.STACKED ? FileModeSettingsData.STACKED : FileModeSettingsData.COLUMNS)
            .withShown(shown.get(0), shown.get(1), shown.get(2))
            .withSizes(columnSizes, stackedWidths, stackedHeights);
    }

    public boolean isShown(Panel panel) {
        return shown.get(panel.ordinal());
    }

    private int shownCount() {
        int n = 0;
        for (boolean b : shown) if (b) n++;
        return n;
    }

    /** True if the panel is shown and is not the last one shown. */
    public boolean canHide(Panel panel) {
        return isShown(panel) && shownCount() > 1;
    }

    /** The panel shown or hidden; unchanged if that would hide the last shown panel. */
    public FileModeLayout withShown(Panel panel, boolean show) {
        if (isShown(panel) == show || (!show && !canHide(panel))) return this;
        List<Boolean> flags = new ArrayList<>(shown);
        flags.set(panel.ordinal(), show);
        return new FileModeLayout(arrangement, flags, columnSizes, stackedWidths, stackedHeights);
    }

    /** Only the panels of the preset shown. */
    public FileModeLayout withPreset(Preset preset) {
        List<Boolean> flags = new ArrayList<>();
        for (Panel p : Panel.values()) flags.add(preset.panels().contains(p));
        return new FileModeLayout(arrangement, flags, columnSizes, stackedWidths, stackedHeights);
    }

    public FileModeLayout withArrangement(Arrangement a) {
        return new FileModeLayout(a, shown, columnSizes, stackedWidths, stackedHeights);
    }

    /** The default sizes, same panels shown. */
    public FileModeLayout withDefaultSizes() {
        FileModeLayout d = defaults();
        return new FileModeLayout(arrangement, shown, d.columnSizes, d.stackedWidths, d.stackedHeights);
    }

    /** The horizontal split: the three columns, or the file list and the stack. */
    public ColumnLayout outer() {
        if (arrangement == Arrangement.COLUMNS) return new ColumnLayout(shown, columnSizes);
        return new ColumnLayout(List.of(isShown(Panel.FILES), isShown(Panel.EDITOR) || isShown(Panel.OBJECTS)),
            stackedWidths);
    }

    /** The vertical split of the stack (editor, objects); meaningful when stacked. */
    public ColumnLayout inner() {
        return new ColumnLayout(List.of(isShown(Panel.EDITOR), isShown(Panel.OBJECTS)), stackedHeights);
    }

    /** The layout after the user changed the horizontal split (drag, double-click). */
    public FileModeLayout withOuter(ColumnLayout outer) {
        if (arrangement == Arrangement.COLUMNS) {
            if (outer.count() != 3) return this;
            return new FileModeLayout(arrangement, outer.shown(), outer.sizes(), stackedWidths, stackedHeights);
        }
        if (outer.count() != 2 || outer.shownPanes().isEmpty()) return this;
        boolean files = outer.isShown(0);
        boolean editor = isShown(Panel.EDITOR);
        boolean objects = isShown(Panel.OBJECTS);
        if (!outer.isShown(1)) {
            editor = objects = false;
        } else if (!editor && !objects) {
            editor = true;
        }
        return new FileModeLayout(arrangement, List.of(files, editor, objects), columnSizes, outer.sizes(), stackedHeights);
    }

    /** The layout after the user changed the vertical split of the stack. */
    public FileModeLayout withInner(ColumnLayout inner) {
        if (inner.count() != 2) return this;
        boolean files = isShown(Panel.FILES);
        if (!files && inner.shownPanes().isEmpty()) return this;
        return new FileModeLayout(arrangement, List.of(files, inner.isShown(0), inner.isShown(1)), columnSizes,
            stackedWidths, inner.sizes());
    }

    /** The panels of the layout for the toggles and menu items, in {@link Panel} order. */
    public static PanelModel<FileModeLayout> panels() {
        return new PanelModel<>() {
            @Override
            public boolean isShown(FileModeLayout layout, int panel) {
                return layout.isShown(Panel.values()[panel]);
            }

            @Override
            public boolean canHide(FileModeLayout layout, int panel) {
                return layout.canHide(Panel.values()[panel]);
            }

            @Override
            public FileModeLayout withShown(FileModeLayout layout, int panel, boolean show) {
                return layout.withShown(Panel.values()[panel], show);
            }
        };
    }

    private static double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i) == null ? 0 : values.get(i);
        return result;
    }
}
