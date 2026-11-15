package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.gui.layout.PanelToggles;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.TagBrowserMode;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileModeLayout;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileModeLayout.Panel;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.settings.FileModeSettingsData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** File Mode's three panels, their toggles in the main toolbar and the Panels menu. */
@Tag("gui")
class FileModeLayoutGuiTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private FileMode fileMode() {
        return (FileMode) workbench().modes().orElseThrow().get(Modes.FILE);
    }

    private FileModeLayout layout() {
        return fileMode().layoutProperty().get();
    }

    private SplitPane split(FxRobot robot) {
        return robot.lookup("#fileModeSplit").queryAs(SplitPane.class);
    }

    private ToggleButton toggle(FxRobot robot, String id) {
        return robot.lookup("#" + id).queryAs(ToggleButton.class);
    }

    private Menu panelsMenu(FxRobot robot) {
        Menu fileModeMenu = robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> "File Mode".equals(m.getText())).findFirst().orElseThrow();
        return (Menu) fileModeMenu.getItems().stream().filter(i -> "Panels".equals(i.getText())).findFirst().orElseThrow();
    }

    private MenuItem menuItem(FxRobot robot, String id) {
        return panelsMenu(robot).getItems().stream().filter(i -> id.equals(i.getId())).findFirst().orElseThrow();
    }

    private void press(FxRobot robot, KeyCombination combination) {
        KeyCodeCombination c = (KeyCodeCombination) combination;
        List<KeyCode> keys = new ArrayList<>();
        if (c.getShortcut() == KeyCombination.ModifierValue.DOWN) keys.add(KeyCode.SHORTCUT);
        if (c.getControl() == KeyCombination.ModifierValue.DOWN) keys.add(KeyCode.CONTROL);
        if (c.getAlt() == KeyCombination.ModifierValue.DOWN) keys.add(KeyCode.ALT);
        if (c.getShift() == KeyCombination.ModifierValue.DOWN) keys.add(KeyCode.SHIFT);
        keys.add(c.getCode());
        robot.press(keys.toArray(KeyCode[]::new));
        robot.release(keys.reversed().toArray(KeyCode[]::new));
    }

    @Test
    void threeColumnsInOrderWithoutTheDeadControls(FxRobot robot) {
        SplitPane split = split(robot);
        assertEquals(3, split.getItems().size());
        assertTrue(robot.lookup("#fileTreeView").query().isVisible());
        assertSame(split.getItems().get(0), ancestorIn(split, robot.lookup("#fileTreeView").query()));
        assertSame(split.getItems().get(1), ancestorIn(split, robot.lookup("#editorScrollPane").query()));
        assertSame(split.getItems().get(2), ancestorIn(split, robot.lookup("#objectTabPane").query()));
        assertTrue(robot.lookup((Node n) -> n instanceof Labeled l && l.getText() != null
            && l.getText().startsWith("Insert intermorphemic")).tryQuery().isEmpty());
        List<String> tabs = robot.lookup("#objectTabPane").queryAs(TabPane.class).getTabs().stream().map(Tab::getText).toList();
        assertFalse(tabs.contains("Session"));
        assertFalse(tabs.contains("Tag"));
        Menu fileModeMenu = (Menu) panelsMenu(robot).getParentMenu();
        assertTrue(fileModeMenu.getItems().stream().noneMatch(i -> "Insert".equals(i.getText())));
    }

    private static Node ancestorIn(SplitPane split, Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (split.getItems().contains(n)) return n;
        }
        return null;
    }

    @Test
    void togglesAreInTheMainToolbarOnlyInFileMode(FxRobot robot) {
        ToolBar toolBar = robot.lookup("#toolBar").queryAs(ToolBar.class);
        Node slot = robot.lookup("#modeToolBarSlot").query();
        assertTrue(toolBar.getItems().contains(slot));
        assertEquals(List.of("Files", "Editor", "Objects"), List.of(toggle(robot, "fileListToggle").getText(),
            toggle(robot, "fileEditorToggle").getText(), toggle(robot, "fileObjectsToggle").getText()));
        assertTrue(toggle(robot, "fileListToggle").isSelected());
        assertTrue(toggle(robot, "fileEditorToggle").isSelected());
        assertTrue(toggle(robot, "fileObjectsToggle").isSelected());

        robot.interact(() -> workbench().navigator().switchTo(TagBrowserMode.ID));
        assertTrue(robot.lookup("#fileListToggle").tryQuery().isEmpty());
        assertTrue(robot.lookup("#tagTreeToggle").tryQuery().isPresent());
        robot.interact(() -> workbench().navigator().switchTo(Modes.FILE));
        assertTrue(robot.lookup("#fileListToggle").tryQuery().isPresent());
        assertTrue(robot.lookup("#tagTreeToggle").tryQuery().isEmpty());

        robot.interact(() -> workbench().session().closeProject());
        assertFalse(slot.isVisible());
    }

    @Test
    void hidingAColumnKeepsTheOthersAndShowingRestoresIt(FxRobot robot) {
        double[] before = split(robot).getDividerPositions();
        robot.clickOn("#fileListToggle");
        assertEquals(2, split(robot).getItems().size());
        assertFalse(layout().isShown(Panel.FILES));
        // editor and objects share the room as before: 0.47 / 0.35
        assertEquals(0.47 / 0.82, split(robot).getDividerPositions()[0], 0.02);
        robot.clickOn("#fileListToggle");
        assertEquals(3, split(robot).getItems().size());
        assertArrayEquals(before, split(robot).getDividerPositions(), 0.01);
        // remembered in the settings
        assertEquals(true, settings.fileModeProperty().get().showFiles());
        robot.clickOn("#fileObjectsToggle");
        assertEquals(false, settings.fileModeProperty().get().showObjects());
    }

    @Test
    void theLastPanelCannotBeHidden(FxRobot robot) {
        robot.clickOn("#fileListToggle");
        robot.clickOn("#fileObjectsToggle");
        assertEquals(1, split(robot).getItems().size());
        assertTrue(toggle(robot, "fileEditorToggle").isDisable());
        assertTrue(menuItem(robot, "fileEditorToggleItem").isDisable());
        assertFalse(toggle(robot, "fileListToggle").isDisable());
    }

    @Test
    void menuItemsAndShortcutsFollowTheToggles(FxRobot robot) {
        javafx.scene.control.CheckMenuItem files = (javafx.scene.control.CheckMenuItem) menuItem(robot, "fileListToggleItem");
        assertTrue(files.isSelected());
        robot.clickOn("#fileListToggle");
        assertFalse(files.isSelected());
        robot.clickOn("#fileEditorToggle");
        press(robot, PanelToggles.accelerator(3));
        // Objects was the last one shown: refused
        assertTrue(layout().isShown(Panel.OBJECTS));
        press(robot, PanelToggles.accelerator(0));
        assertEquals(FileModeLayout.Preset.ALL.panels().size(), split(robot).getItems().size());
        press(robot, PanelToggles.accelerator(1));
        assertFalse(layout().isShown(Panel.FILES));
        assertFalse(toggle(robot, "fileListToggle").isSelected());
    }

    @Test
    void navigationShowsTheEditorAgain(FxRobot robot) throws IOException {
        Path file = addFile(robot, "notes.txt", "one\ntwo\nthree\n");
        showFile(robot, file);
        robot.interact(() -> fileMode().layoutProperty().set(layout().withPreset(FileModeLayout.Preset.BROWSING)));
        assertFalse(layout().isShown(Panel.EDITOR));
        robot.interact(() -> workbench().navigator().showInEditor(Focus.at(document(file).getId(), 4)));
        assertTrue(layout().isShown(Panel.EDITOR));
        assertTrue(layout().isShown(Panel.FILES));

        // Find with the editor hidden: the editor comes back with the find bar
        robot.interact(() -> fileMode().layoutProperty().set(layout().withShown(Panel.EDITOR, false)));
        MenuItem find = ((Menu) panelsMenu(robot).getParentMenu()).getItems().stream()
            .filter(i -> "Find…".equals(i.getText())).findFirst().orElseThrow();
        robot.interact(find::fire);
        assertTrue(layout().isShown(Panel.EDITOR));
    }

    @Test
    void focusLeavesAHiddenPanel(FxRobot robot) throws IOException {
        Path file = addFile(robot, "notes.txt", "one\n");
        showFile(robot, file);
        robot.interact(() -> robot.lookup("#fileTreeView").query().requestFocus());
        robot.clickOn("#fileListToggle");
        Node owner = stage.getScene().getFocusOwner();
        assertNotNull(owner);
        assertSame(robot.lookup("#editorScrollPane").query(), owner.getParent());
    }

    @Test
    void presetsArrangementAndReset(FxRobot robot) {
        robot.interact(() -> fileMode().layoutProperty().set(layout().withPreset(FileModeLayout.Preset.WRITING)));
        assertEquals(1, split(robot).getItems().size());
        robot.interact(() -> fileMode().layoutProperty().set(layout().withPreset(FileModeLayout.Preset.ALL)
            .withArrangement(FileModeLayout.Arrangement.STACKED)));
        // files | (editor over objects)
        assertEquals(2, split(robot).getItems().size());
        SplitPane stack = robot.lookup("#fileModeStack").queryAs(SplitPane.class);
        assertSame(stack, split(robot).getItems().get(1));
        assertEquals(javafx.geometry.Orientation.VERTICAL, stack.getOrientation());
        assertEquals(2, stack.getItems().size());
        assertEquals(FileModeSettingsData.STACKED, settings.fileModeProperty().get().arrangement());
        // the editor hidden: the stack shows the objects only
        robot.clickOn("#fileEditorToggle");
        assertEquals(1, stack.getItems().size());
        // back to columns, sizes reset
        robot.interact(() -> fileMode().layoutProperty().set(layout().withArrangement(FileModeLayout.Arrangement.COLUMNS)
            .withShown(Panel.EDITOR, true).withDefaultSizes()));
        assertEquals(3, split(robot).getItems().size());
        assertArrayEquals(new double[] {0.18, 0.65}, split(robot).getDividerPositions(), 0.02);
    }

    @Test
    void doubleClickOnADividerCollapsesAndRestores(FxRobot robot) {
        SplitPane split = split(robot);
        Node first = split.getChildrenUnmodifiable().stream()
            .filter(n -> n.getStyleClass().contains("split-pane-divider")).findFirst().orElseThrow();
        robot.doubleClickOn(first);
        assertFalse(layout().isShown(Panel.FILES));
        assertEquals(2, split.getItems().size());
        Node again = split.getChildrenUnmodifiable().stream()
            .filter(n -> n.getStyleClass().contains("split-pane-divider")).findFirst().orElseThrow();
        robot.doubleClickOn(again);
        assertTrue(layout().isShown(Panel.FILES));
        assertEquals(3, split.getItems().size());
    }

    @Test
    void tagBrowserPanels(FxRobot robot) {
        robot.interact(() -> workbench().navigator().switchTo(TagBrowserMode.ID));
        SplitPane split = robot.lookup("#tagBrowserSplit").queryAs(SplitPane.class);
        assertEquals(2, split.getItems().size());
        robot.clickOn("#tagTreeToggle");
        assertEquals(1, split.getItems().size());
        assertTrue(toggle(robot, "tagOccurrencesToggle").isDisable());
        assertEquals(false, settings.tagBrowserProperty().get().showTree());
        robot.clickOn("#tagTreeToggle");
        assertEquals(2, split.getItems().size());
    }
}
