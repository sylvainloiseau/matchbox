package fr.cnrs.lacito.matchbox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileModeSettingsDataTest {

    @Test
    void missingValuesAreDefaults() {
        List<String> problems = new ArrayList<>();
        var v = new FileModeSettingsData(null, null, null, null, null, null, null, null, null).validated(problems::add);
        assertEquals(FileModeSettingsData.defaults(), v);
        assertTrue(problems.isEmpty());
    }

    @Test
    void noPanelShownShowsAll() {
        List<String> problems = new ArrayList<>();
        var v = FileModeSettingsData.defaults().withShown(false, false, false).validated(problems::add);
        assertEquals(true, v.showFiles());
        assertEquals(true, v.showEditor());
        assertEquals(true, v.showObjects());
        assertEquals(1, problems.size());
    }

    @Test
    void invalidValuesAreReplaced() {
        List<String> problems = new ArrayList<>();
        var v = new FileModeSettingsData("diagonal", true, false, true, List.of(0.5, 0.5),
            List.of(0.5, -1.0), List.of(2.0, 2.0), 1.5, Double.NaN).validated(problems::add);
        assertEquals(FileModeSettingsData.COLUMNS, v.arrangement());
        assertEquals(FileModeSettingsData.DEFAULT_COLUMN_WIDTHS, v.columnWidths());
        assertEquals(FileModeSettingsData.DEFAULT_STACKED_WIDTHS, v.stackedWidths());
        // valid sizes are normalised
        assertEquals(List.of(0.5, 0.5), v.stackedHeights());
        assertEquals(0.55, v.objectsSplit());
        assertEquals(0.72, v.suggestionsSplit());
        assertEquals(5, problems.size());
    }

    @Test
    void tagBrowserLayoutDefaultsAndValidation() {
        var old = new TagBrowserSettingsData(3, 4, TagBrowserSettingsData.SORT_NAME, true).validated(p -> {});
        assertEquals(true, old.showTree());
        assertEquals(TagBrowserSettingsData.DEFAULT_WIDTHS, old.widths());
        var hidden = old.withLayout(false, false, List.of(1.0, 3.0)).validated(p -> {});
        assertEquals(true, hidden.showTree());
        assertEquals(true, hidden.showOccurrences());
        assertEquals(List.of(0.25, 0.75), hidden.widths());
    }

    @Test
    void oldSettingsWithoutFileModeGetTheDefaults() {
        SettingsData d = SettingsData.defaults();
        SettingsData old = new SettingsData(d.version(), d.charset(), d.morphemeSeparators(), d.fileExtensions(),
            d.filePatterns(), d.maxRecentFiles(), d.maxRecentProjects(), d.openLastProject(), d.logFile(), d.assist(),
            d.allowInvalidViewEdits(), d.tagBrowser(), d.editor());
        assertEquals(FileModeSettingsData.defaults(), old.validated().fileMode());
    }
}
