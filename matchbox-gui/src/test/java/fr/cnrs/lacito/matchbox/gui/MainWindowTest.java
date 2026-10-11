package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.PluginManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The manual checklist of MODE_PLAN.md §9, on the real window. */
class MainWindowTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private static final String IGT = """
        <mami> {pig}

        :::
        a mio-jaflo-m-wi-j
        1sg down-canoe-top-m.sg-pred
        'I went down in canoe'
        :::
        """;

    private MenuBar menuBar(FxRobot robot) {
        return robot.lookup(".menu-bar").queryAs(MenuBar.class);
    }

    private Menu menu(FxRobot robot, String title) {
        return menuBar(robot).getMenus().stream().filter(m -> title.equals(m.getText())).findFirst()
            .orElseThrow(() -> new AssertionError("No menu " + title));
    }

    private MenuItem item(Menu menu, String prefix) {
        return menu.getItems().stream().filter(i -> i.getText() != null && i.getText().startsWith(prefix)).findFirst()
            .orElseThrow(() -> new AssertionError("No item " + prefix + " in " + menu.getText()));
    }

    private String statusMessage(FxRobot robot) {
        return robot.lookup("#statusMessageLabel").queryAs(Label.class).getText();
    }

    /** The translation field of the IGT detail view. */
    private TextField translationField(FxRobot robot, String value) {
        waitUntil(() -> robot.lookup(".text-field").queryAllAs(TextField.class).stream()
            .anyMatch(t -> value.equals(t.getText())));
        return robot.lookup(".text-field").queryAllAs(TextField.class).stream()
            .filter(t -> value.equals(t.getText())).findFirst().orElseThrow();
    }

    /** Selects the IGT of the displayed file through its row in the objects table. */
    private void selectIgt(FxRobot robot) {
        Node row = robot.lookup(".table-row-cell").match((Node n) ->
            n instanceof javafx.scene.control.TableRow<?> r && r.getItem() instanceof InterlinearSentence).query();
        robot.clickOn(row);
    }

    private void editTranslation(FxRobot robot, String from, String to) {
        TextField field = translationField(robot, from);
        robot.clickOn(field);
        robot.interact(field::selectAll);
        robot.write(to);
        robot.type(KeyCode.ENTER);
    }

    // ------------------------------------------------------------------

    @Test
    void theWindowShowsFileModeItsMenuAndTheModeButtons(FxRobot robot) {
        List<String> menus = menuBar(robot).getMenus().stream().map(Menu::getText).toList();
        assertEquals(List.of("File", "Current project", "Edit", "Mode", "File Mode", "Assistant", "Export"), menus);
        ToggleButton file = robot.lookup(".toggle-button").match((Node n) ->
            n instanceof ToggleButton b && "File".equals(b.getText())).queryAs(ToggleButton.class);
        assertTrue(file.isSelected());
        assertEquals("File", menu(robot, "Mode").getItems().get(0).getText());
        assertEquals(Modes.FILE, workbench().modes().orElseThrow().activeId().orElseThrow());
        // nothing displayed: the File Mode items acting on the file are disabled
        assertTrue(item(menu(robot, "File Mode"), "Find…").isDisable());
        assertTrue(item(menu(robot, "Edit"), "Undo").isDisable());
    }

    @Test
    void undoIsProjectWideAndLabelledWithTheFile(FxRobot robot) throws Exception {
        Path a = addFile(robot, "a.txt", IGT);
        Path b = addFile(robot, "b.txt", "plain notes\n");
        showFile(robot, a);
        selectIgt(robot);
        editTranslation(robot, "I went down in canoe", "I went by canoe");
        waitUntil(() -> document(a).getCurrentText().contains("'I went by canoe'"));

        showFile(robot, b);
        robot.clickOn("#fileContentCodeArea");
        robot.type(KeyCode.END);
        robot.write("xyz");
        waitUntil(() -> document(b).getCurrentText().contains("xyz"));

        MenuItem undo = item(menu(robot, "Edit"), "Undo");
        waitUntil(() -> "Undo Typing (b.txt)".equals(undo.getText()));
        robot.interact(undo::fire);
        waitUntil(() -> "Undo Edit free translation (a.txt)".equals(undo.getText()));
        assertEquals("plain notes\n", document(b).getCurrentText());

        robot.interact(undo::fire);
        waitUntil(() -> document(a).getCurrentText().contains("'I went down in canoe'"));
        assertTrue(undo.isDisable());
        assertEquals("Redo Edit free translation (a.txt)", item(menu(robot, "Edit"), "Redo").getText());
        // the change was made in a file that is not displayed
        assertEquals("Undone: Edit free translation (a.txt)", statusMessage(robot));
    }

    @Test
    void theShowLinkOpensTheFileOfAnUndoneChange(FxRobot robot) throws Exception {
        Path a = addFile(robot, "a.txt", IGT);
        Path b = addFile(robot, "b.txt", "plain notes\n");
        showFile(robot, a);
        selectIgt(robot);
        editTranslation(robot, "I went down in canoe", "changed");
        waitUntil(() -> document(a).getCurrentText().contains("'changed'"));
        showFile(robot, b);

        // Ctrl+Z (⌘Z) in the editor undoes the last change of the project, here in a.txt
        robot.clickOn("#fileContentCodeArea");
        robot.press(KeyCode.SHORTCUT, KeyCode.Z).release(KeyCode.Z, KeyCode.SHORTCUT);
        waitUntil(() -> document(a).getCurrentText().contains("'I went down in canoe'"));
        Hyperlink show = robot.lookup("#statusLink").queryAs(Hyperlink.class);
        waitUntil(show::isVisible);
        assertEquals("Show", show.getText());
        robot.clickOn(show);
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(a).getId())).orElse(false));
    }

    @Test
    void showInEditorThenBack(FxRobot robot) throws Exception {
        Path a = addFile(robot, "a.txt", IGT);
        Path b = addFile(robot, "b.txt", "plain notes\n");
        showFile(robot, b);
        Button back = robot.lookup("#backButton").queryAs(Button.class);
        assertTrue(back.isDisable());

        // a view asks to show a span of another file
        var igt = workbench().session().corpus().text(document(a).getId()).orElseThrow()
            .objectsOf(InterlinearSentence.class).get(0);
        robot.interact(() -> workbench().viewContext().selectInEditor(igt.getTextCoordinate()));
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(a).getId())).orElse(false));
        assertTrue(robot.lookup("#focusLabel").queryAs(Label.class).getText().startsWith("a.txt:"));

        waitUntil(() -> !back.isDisable());
        robot.clickOn(back);
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(b).getId())).orElse(false));
        assertFalse(robot.lookup("#forwardButton").queryAs(Button.class).isDisable());
    }

    @Test
    void playMediaSegmentReportsWhatItCannotPlay(FxRobot robot) throws Exception {
        MenuItem play = item(menu(robot, "Edit"), "Play media segment");
        robot.interact(play::fire);
        assertEquals("No media segment here", statusMessage(robot));

        Path m = addFile(robot, "media.txt", "@media missing.wav 0:01-0:02 hello\n");
        showFile(robot, m);
        robot.interact(play::fire);
        waitUntil(() -> statusMessage(robot).startsWith("File not found"));
    }

    @Test
    void aNewProjectEmptiesTheWindow(FxRobot robot) throws Exception {
        Path a = addFile(robot, "a.txt", "plain notes\n");
        showFile(robot, a);
        robot.clickOn("#fileContentCodeArea");
        robot.write("x");
        MenuItem undo = item(menu(robot, "Edit"), "Undo");
        waitUntil(() -> !undo.isDisable());

        Project next = Project.createDefaultProject(PluginManager.loadPlugins());
        robot.interact(() -> workbench().session().setProject(next));
        waitUntil(() -> workbench().focus().isEmpty());
        assertTrue(undo.isDisable(), "the journal is the new project's");
        assertEquals("", robot.lookup("#focusLabel").queryAs(Label.class).getText());
        assertTrue(robot.lookup("#fileTreeView").queryAs(javafx.scene.control.TreeView.class).getRoot().getChildren().isEmpty());
    }

    @Test
    void saveProjectWritesTheProjectFileWithoutAsking(FxRobot robot) throws Exception {
        // a project saved earlier, opened again
        Project saved = Project.createDefaultProject(PluginManager.loadPlugins());
        Path mbx = dir.resolve("p.mbx");
        saved.setFile(mbx.toFile());
        fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer.save(saved);
        Project opened = Project.loadProject(PluginManager.loadPlugins(), mbx.toFile());
        robot.interact(() -> workbench().session().setProject(opened));
        Path a = addFile(robot, "a.txt", "notes\n");

        MenuItem save = item(menu(robot, "File"), "Save project");
        assertFalse(save.isDisable());
        int windows = Window.getWindows().size();
        robot.interact(save::fire);
        assertEquals(windows, Window.getWindows().size(), "no file chooser, no dialog");
        assertTrue(java.nio.file.Files.readString(mbx).contains(a.getFileName().toString()), "the file added is saved");
    }

    @Test
    void theFileModeMenuActsOnTheDisplayedFile(FxRobot robot) throws Exception {
        Menu fileMode = menu(robot, "File Mode");
        assertTrue(item(fileMode, "Find…").isDisable(), "no file displayed");
        // the first file added is displayed
        Path a = addFile(robot, "a.txt", IGT);
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(a).getId())).orElse(false));
        MenuItem find = item(fileMode, "Find…");
        assertFalse(find.isDisable());
        assertFalse(item(fileMode, "Review whole file").isDisable());
        robot.interact(find::fire);
        waitUntil(() -> robot.lookup("#findBar").query().isVisible());
    }
}
