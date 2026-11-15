package fr.cnrs.lacito.matchbox.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Layout of File Mode, part of {@link SettingsData}: which panels are shown (file list, editor,
 * objects) and their sizes. Immutable; global to every project.
 *
 * @param arrangement     "columns" (objects beside the editor) or "stacked" (objects under it)
 * @param showFiles       the file list is shown
 * @param showEditor      the editor is shown
 * @param showObjects     the objects and problems tabs are shown
 * @param columnWidths    widths of the three columns ("columns"), fractions summing to 1
 * @param stackedWidths   widths of the file list and of the editor/objects stack ("stacked")
 * @param stackedHeights  heights of the editor and of the objects in the stack ("stacked")
 * @param objectsSplit    divider between the object tables and the detail view (0–1)
 * @param suggestionsSplit divider between the editor and the suggestions panel (0–1)
 */
public record FileModeSettingsData(String arrangement, Boolean showFiles, Boolean showEditor, Boolean showObjects,
                                   List<Double> columnWidths, List<Double> stackedWidths, List<Double> stackedHeights,
                                   Double objectsSplit, Double suggestionsSplit) {

    public static final String COLUMNS = "columns";
    public static final String STACKED = "stacked";
    public static final List<Double> DEFAULT_COLUMN_WIDTHS = List.of(0.18, 0.47, 0.35);
    public static final List<Double> DEFAULT_STACKED_WIDTHS = List.of(0.2, 0.8);
    public static final List<Double> DEFAULT_STACKED_HEIGHTS = List.of(0.55, 0.45);

    public static FileModeSettingsData defaults() {
        return new FileModeSettingsData(COLUMNS, true, true, true, DEFAULT_COLUMN_WIDTHS, DEFAULT_STACKED_WIDTHS,
            DEFAULT_STACKED_HEIGHTS, 0.55, 0.72);
    }

    /** Copy where missing or invalid values are replaced by the defaults. */
    public FileModeSettingsData validated(Consumer<String> problems) {
        FileModeSettingsData d = defaults();
        String a = d.arrangement;
        if (COLUMNS.equals(arrangement) || STACKED.equals(arrangement)) {
            a = arrangement;
        } else if (arrangement != null) {
            problems.accept("Invalid fileMode.arrangement '" + arrangement + "', using " + d.arrangement);
        }
        boolean files = showFiles == null ? d.showFiles : showFiles;
        boolean editor = showEditor == null ? d.showEditor : showEditor;
        boolean objects = showObjects == null ? d.showObjects : showObjects;
        if (!files && !editor && !objects) {
            problems.accept("fileMode: no panel shown, showing all of them");
            files = editor = objects = true;
        }
        return new FileModeSettingsData(a, files, editor, objects,
            sizes(columnWidths, d.columnWidths, "fileMode.columnWidths", problems),
            sizes(stackedWidths, d.stackedWidths, "fileMode.stackedWidths", problems),
            sizes(stackedHeights, d.stackedHeights, "fileMode.stackedHeights", problems),
            fraction(objectsSplit, d.objectsSplit, "fileMode.objectsSplit", problems),
            fraction(suggestionsSplit, d.suggestionsSplit, "fileMode.suggestionsSplit", problems));
    }

    /**
     * Sizes of panes: as many as the defaults, each above 0.02, normalised to sum 1; otherwise the
     * defaults. Shared with the other layouts of the settings.
     */
    static List<Double> sizes(List<Double> values, List<Double> fallback, String name, Consumer<String> problems) {
        if (values == null) return fallback;
        if (values.size() != fallback.size()) {
            problems.accept("Invalid " + name + " " + values + ", using " + fallback);
            return fallback;
        }
        double total = 0;
        for (Double v : values) {
            if (v == null || v.isNaN() || v.isInfinite() || v <= 0.02) {
                problems.accept("Invalid " + name + " " + values + ", using " + fallback);
                return fallback;
            }
            total += v;
        }
        if (Math.abs(total - 1) < 1e-9) return List.copyOf(values);
        List<Double> result = new ArrayList<>();
        for (Double v : values) result.add(v / total);
        return List.copyOf(result);
    }

    private static double fraction(Double value, double fallback, String name, Consumer<String> problems) {
        if (value == null) return fallback;
        if (value.isNaN() || value <= 0.05 || value >= 0.95) {
            problems.accept("Invalid " + name + " " + value + ", using " + fallback);
            return fallback;
        }
        return value;
    }

    public FileModeSettingsData withArrangement(String a) {
        return new FileModeSettingsData(a, showFiles, showEditor, showObjects, columnWidths, stackedWidths,
            stackedHeights, objectsSplit, suggestionsSplit);
    }

    public FileModeSettingsData withShown(boolean files, boolean editor, boolean objects) {
        return new FileModeSettingsData(arrangement, files, editor, objects, columnWidths, stackedWidths,
            stackedHeights, objectsSplit, suggestionsSplit);
    }

    public FileModeSettingsData withSizes(List<Double> columns, List<Double> stackedW, List<Double> stackedH) {
        return new FileModeSettingsData(arrangement, showFiles, showEditor, showObjects, columns, stackedW, stackedH,
            objectsSplit, suggestionsSplit);
    }

    public FileModeSettingsData withObjectsSplit(double split) {
        return new FileModeSettingsData(arrangement, showFiles, showEditor, showObjects, columnWidths, stackedWidths,
            stackedHeights, split, suggestionsSplit);
    }

    public FileModeSettingsData withSuggestionsSplit(double split) {
        return new FileModeSettingsData(arrangement, showFiles, showEditor, showObjects, columnWidths, stackedWidths,
            stackedHeights, objectsSplit, split);
    }
}
