package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileModeLayout.Arrangement;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileModeLayout.Panel;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileModeLayout.Preset;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemColumns;
import fr.cnrs.lacito.matchbox.settings.FileModeSettingsData;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileModeLayoutTest {

    private final FileModeLayout defaults = FileModeLayout.defaults();

    @Test
    void defaultsAreThreeColumns() {
        assertEquals(Arrangement.COLUMNS, defaults.arrangement());
        assertEquals(List.of(0, 1, 2), defaults.outer().shownPanes());
        assertEquals(FileModeSettingsData.DEFAULT_COLUMN_WIDTHS, defaults.outer().sizes());
    }

    @Test
    void lastPanelStaysShown() {
        FileModeLayout writing = defaults.withPreset(Preset.WRITING);
        assertEquals(List.of(1), writing.outer().shownPanes());
        assertFalse(writing.canHide(Panel.EDITOR));
        assertSame(writing, writing.withShown(Panel.EDITOR, false));
    }

    @Test
    void presets() {
        assertEquals(List.of(1, 2), defaults.withPreset(Preset.REVIEWING).outer().shownPanes());
        assertEquals(List.of(0, 2), defaults.withPreset(Preset.BROWSING).outer().shownPanes());
        assertEquals(defaults, defaults.withPreset(Preset.BROWSING).withPreset(Preset.ALL));
    }

    @Test
    void stackedSplits() {
        FileModeLayout stacked = defaults.withArrangement(Arrangement.STACKED);
        assertEquals(2, stacked.outer().count());
        assertEquals(List.of(0, 1), stacked.inner().shownPanes());
        // editor hidden: the stack still shows the objects
        FileModeLayout noEditor = stacked.withShown(Panel.EDITOR, false);
        assertEquals(List.of(0, 1), noEditor.outer().shownPanes());
        assertEquals(List.of(1), noEditor.inner().shownPanes());
        // editor and objects hidden: the stack is hidden
        FileModeLayout filesOnly = noEditor.withShown(Panel.OBJECTS, false);
        assertEquals(List.of(0), filesOnly.outer().shownPanes());
        // the flags are shared by the two arrangements
        assertEquals(List.of(0), filesOnly.withArrangement(Arrangement.COLUMNS).outer().shownPanes());
    }

    @Test
    void userChangesOfTheSplits() {
        FileModeLayout stacked = defaults.withArrangement(Arrangement.STACKED);
        // double-click hides the stack in the outer split: editor and objects hidden
        FileModeLayout hidden = stacked.withOuter(stacked.outer().withShown(1, false));
        assertFalse(hidden.isShown(Panel.EDITOR));
        assertFalse(hidden.isShown(Panel.OBJECTS));
        // shown again: the editor comes back
        assertTrue(hidden.withOuter(hidden.outer().withShown(1, true)).isShown(Panel.EDITOR));
        // inner drag
        FileModeLayout dragged = stacked.withInner(stacked.inner().dragged(new double[] {0.3}));
        assertEquals(0.3, dragged.stackedHeights().get(0), 1e-9);
        // columns: drags go into the column sizes
        FileModeLayout cols = defaults.withOuter(defaults.outer().dragged(new double[] {0.1, 0.5}));
        assertEquals(0.1, cols.columnSizes().get(0), 1e-9);
        // files hidden and the stack emptied from inside: refused
        FileModeLayout onlyStack = stacked.withShown(Panel.FILES, false).withShown(Panel.OBJECTS, false);
        assertSame(onlyStack, onlyStack.withInner(new fr.cnrs.lacito.matchbox.gui.layout.ColumnLayout(
            List.of(false, false), onlyStack.stackedHeights())));
    }

    @Test
    void settingsRoundTrip() {
        FileModeLayout l = defaults.withArrangement(Arrangement.STACKED).withShown(Panel.FILES, false)
            .withInner(defaults.inner().dragged(new double[] {0.25}));
        FileModeSettingsData data = l.into(FileModeSettingsData.defaults());
        assertEquals(l, FileModeLayout.of(data));
        assertEquals(FileModeSettingsData.defaults().objectsSplit(), data.objectsSplit());
        assertEquals(defaults, l.withArrangement(Arrangement.COLUMNS).withShown(Panel.FILES, true)
            .withDefaultSizes());
    }

    @Test
    void compactProblemColumns() {
        assertTrue(ProblemColumns.isCompact(400));
        assertFalse(ProblemColumns.isCompact(900));
        // not laid out yet
        assertFalse(ProblemColumns.isCompact(0));
    }
}
