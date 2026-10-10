package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The window without project (start page), the project menus and closing a project. */
class StartPageTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    @Override
    protected boolean openProjectAtStart() {
        return false;
    }

    private Menu menu(FxRobot robot, String title) {
        return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> title.equals(m.getText())).findFirst().orElseThrow(() -> new AssertionError("No menu " + title));
    }

    private MenuItem item(Menu menu, String prefix) {
        return menu.getItems().stream().filter(i -> i.getText() != null && i.getText().replace("_", "").startsWith(prefix)).findFirst()
            .orElseThrow(() -> new AssertionError("No item " + prefix + " in " + menu.getText()));
    }

    private boolean startPageShown(FxRobot robot) {
        return robot.lookup("#startPage").query().isVisible();
    }

    private void newProject(FxRobot robot) {
        robot.clickOn("#startNewProject");
        waitUntil(() -> project() != null);
    }

    @Test
    void withoutProjectOnlyTheProjectAndApplicationCommandsAreEnabled(FxRobot robot) {
        assertNull(project());
        assertTrue(startPageShown(robot));
        assertFalse(robot.lookup("#viewPane").query().isVisible());
        Menu file = menu(robot, "File");
        for (String enabled : List.of("New project", "Open project", "Open recent project", "Settings…", "Quit")) {
            assertFalse(item(file, enabled).isDisable(), enabled);
        }
        for (String disabled : List.of("Save project", "Save project as", "Close project")) {
            assertTrue(item(file, disabled).isDisable(), disabled);
        }
        for (String m : List.of("Current project", "Edit", "Mode", "File Mode", "Export")) {
            assertTrue(menu(robot, m).isDisable(), m);
        }
        Menu assistant = menu(robot, "Assistant");
        assertTrue(item(assistant, "Refresh project examples").isDisable());
        assertFalse(item(assistant, "AI assistant settings").isDisable());
        robot.lookup("#toolBar .toggle-button").queryAllAs(ToggleButton.class)
            .forEach(b -> assertTrue(b.isDisable(), b.getText()));
        assertEquals("Matchbox", stage.getTitle());
    }

    @Test
    void aNewProjectShowsFileModeAndEnablesTheMenus(FxRobot robot) {
        newProject(robot);
        assertFalse(startPageShown(robot));
        assertEquals(Modes.FILE, workbench().modes().orElseThrow().activeId().orElseThrow());
        assertFalse(menu(robot, "Current project").isDisable());
        assertFalse(menu(robot, "File Mode").isDisable());
        // New / Open stay enabled: they ask about the open project first
        assertFalse(item(menu(robot, "File"), "Open project").isDisable());
        assertEquals("Matchbox — New project", stage.getTitle());
        assertTrue(closeQuestions.isEmpty(), "nothing to save before");
    }

    @Test
    void closingAnUnchangedProjectAsksNothing(FxRobot robot) {
        newProject(robot);
        robot.interact(item(menu(robot, "File"), "Close project")::fire);
        waitUntil(() -> project() == null);
        assertTrue(startPageShown(robot));
        assertTrue(closeQuestions.isEmpty());
        assertEquals("Matchbox", stage.getTitle());
    }

    @Test
    void closingAProjectWithAModifiedFileAsksAndCancelKeepsIt(FxRobot robot) throws IOException {
        newProject(robot);
        Path a = addFile(robot, "a.txt", "hello\n");
        TextDocument d = document(a);
        robot.interact(() -> d.applyEdits(List.of(TextEdit.replace(d.getCurrentText(), 0, 5, "HELLO")),
            EditOrigin.of(EditOrigin.Kind.VIEW, "test")));
        waitUntil(() -> stage.getTitle().endsWith("•"));

        closeAnswer = Optional.empty();
        robot.interact(item(menu(robot, "File"), "Close project")::fire);
        assertEquals(1, closeQuestions.size());
        assertEquals(List.of(d), closeQuestions.get(0).dirty());
        assertTrue(closeQuestions.get(0).definitionChanged(), "a file was added");
        assertNotNull(project(), "cancelled");

        // New project asks too
        closeAnswer = Optional.of(new fr.cnrs.lacito.matchbox.gui.close.CloseChoice(false, List.of()));
        Project before = project();
        robot.interact(item(menu(robot, "File"), "New project")::fire);
        waitUntil(() -> project() != before);
        assertEquals(2, closeQuestions.size());
    }

    @Test
    void aRecentProjectOpensFromTheStartPage(FxRobot robot) throws Exception {
        Project saved = Project.createDefaultProject(PluginManager.loadPlugins());
        saved.setName("fieldwork");
        Path mbx = dir.resolve("fieldwork.mbx");
        saved.setFile(mbx.toFile());
        ProjectXMLSerializer.save(saved);
        robot.interact(() -> settings.recentProjects().add(mbx));
        Node link = robot.lookup("#startRecentProjects .hyperlink").match((Node n) ->
            n instanceof Hyperlink h && "fieldwork.mbx".equals(h.getText())).query();
        robot.clickOn(link);
        waitUntil(() -> project() != null);
        assertEquals("fieldwork", project().getName());
        assertEquals("Matchbox — fieldwork", stage.getTitle());
        assertFalse(project().definitionChangedProperty().get());
    }

    @Test
    void theMenusAreSplitBetweenFileAndCurrentProject(FxRobot robot) {
        newProject(robot);
        List<String> file = menu(robot, "File").getItems().stream().map(MenuItem::getText).filter(t -> t != null).toList();
        assertEquals(List.of("_New project", "_Open project…", "Open recent project", "Save project", "Save project as…",
            "Close project", "Settings…", "Quit"), file);
        List<String> current = menu(robot, "Current project").getItems().stream().map(MenuItem::getText)
            .filter(t -> t != null).toList();
        assertEquals(List.of("Project properties…", "Add _file…", "Add files in _directory…", "Add recent",
            "_Save current file", "Save all files", "Remove file from project", "Remove directory from project…",
            "Remove all files from project…"), current);
    }

    @Test
    void theSettingsDialogHasNoColourTab(FxRobot robot) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/cnrs/lacito/matchbox/gui/SettingsDialog.fxml"));
        loader.setController(new SettingsDialog(settings, stage));
        loader.load();
        TabPane tabs = (TabPane) loader.getNamespace().get("tabs");
        assertEquals(List.of("Grammar", "Files", "Application"), tabs.getTabs().stream().map(Tab::getText).toList());
    }
}
