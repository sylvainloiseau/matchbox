package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree;
import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** File Mode's file tree, the directory settings and the directory commands. */
class FileTreeGuiTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private Path notes;

    private Menu menu(FxRobot robot, String title) {
        return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> title.equals(m.getText())).findFirst().orElseThrow();
    }

    private MenuItem item(Menu menu, String prefix) {
        return menu.getItems().stream().filter(i -> i.getText() != null && i.getText().replace("_", "").startsWith(prefix))
            .findFirst().orElseThrow(() -> new AssertionError("No item " + prefix));
    }

    @SuppressWarnings("unchecked")
    private TreeView<FileTree.Node> tree(FxRobot robot) {
        return robot.lookup("#fileTreeView").queryAs(TreeView.class);
    }

    private List<String> contextMenu(FxRobot robot, Path path) {
        ContextMenu menu = ((TreeCell<?>) treeCell(robot, path)).getContextMenu();
        return menu.getItems().stream().filter(i -> i.getText() != null)
            .map(i -> i.getText() + (i.isDisable() ? " (disabled)" : "")).toList();
    }

    private void fireContextItem(FxRobot robot, Path path, String text) {
        MenuItem i = ((TreeCell<?>) treeCell(robot, path)).getContextMenu().getItems().stream()
            .filter(m -> text.equals(m.getText())).findFirst().orElseThrow();
        robot.interact(i::fire);
    }

    /** notes/ with A.txt, b.txt, 2024/day1.txt, added through Current project › Add files in directory…. */
    private void addNotes(FxRobot robot) throws IOException {
        notes = Files.createDirectories(dir.resolve("notes"));
        Files.createDirectories(notes.resolve("2024"));
        Files.writeString(notes.resolve("A.txt"), "a\n");
        Files.writeString(notes.resolve("b.txt"), "b\n");
        Files.writeString(notes.resolve("2024/day1.txt"), "d\n");
        chosenDirectory = notes.toFile();
        directorySettingsAnswer = s -> new DirectorySettings(List.of("*.txt"), List.of(), true);
        robot.interact(item(menu(robot, "Current project"), "Add files in directory")::fire);
        waitUntil(() -> project().getDefinitionDirectories().contains(notes));
    }

    @Test
    void anAddedDirectoryGroupsItsFilesAndSubDirectories(FxRobot robot) throws IOException {
        Path alone = Files.writeString(dir.resolve("alone.txt"), "x\n");
        addNotes(robot);
        addFile(robot, "alone.txt", "x\n");
        waitUntil(() -> tree(robot).getRoot().getChildren().size() == 2);
        TreeItem<FileTree.Node> top = tree(robot).getRoot().getChildren().get(0);
        assertEquals(new FileTree.TopDirectory(notes), top.getValue());
        assertTrue(top.isExpanded(), "top directories are expanded at first");
        assertEquals(List.of("2024", "A.txt", "b.txt"), top.getChildren().stream().map(i -> i.getValue().name()).toList());
        assertEquals(alone, tree(robot).getRoot().getChildren().get(1).getValue().path());
        assertTrue(project().directorySettings(notes).orElseThrow().recursive());
    }

    @Test
    void theContextMenusDependOnTheNode(FxRobot robot) throws IOException {
        addNotes(robot);
        assertEquals(List.of("Edit directory settings…", "Rescan directory", "Remove this directory from the project"),
            contextMenu(robot, notes));
        assertEquals(List.of("Exclude this sub-directory"), contextMenu(robot, notes.resolve("2024")));
        assertEquals(List.of("Remove file from project (disabled)", "Exclude this file"),
            contextMenu(robot, notes.resolve("A.txt")));
    }

    @Test
    void aFileOfADirectoryCannotBeRemovedAlone(FxRobot robot) throws IOException {
        addNotes(robot);
        showFile(robot, notes.resolve("A.txt"));
        MenuItem remove = item(menu(robot, "Current project"), "Remove file from project");
        assertTrue(remove.isDisable());
        assertTrue(robot.lookup("#closeButton").query().isDisable());
        Path alone = addFile(robot, "alone.txt", "x\n");
        waitUntil(() -> tree(robot).getRoot().getChildren().size() == 2);
        showFile(robot, alone);
        assertFalse(remove.isDisable());
    }

    @Test
    void excludingAFileUpdatesTheSettingsAndTheTree(FxRobot robot) throws IOException {
        addNotes(robot);
        fireContextItem(robot, notes.resolve("A.txt"), "Exclude this file");
        waitUntil(() -> project().filesOf(notes).size() == 2);
        assertEquals(List.of("/A.txt"), project().directorySettings(notes).orElseThrow().excludes());
        assertEquals(List.of("2024", "b.txt"),
            tree(robot).getRoot().getChildren().get(0).getChildren().stream().map(i -> i.getValue().name()).toList());
        fireContextItem(robot, notes.resolve("2024"), "Exclude this sub-directory");
        waitUntil(() -> project().filesOf(notes).size() == 1);
        assertEquals(List.of("/A.txt", "/2024/"), project().directorySettings(notes).orElseThrow().excludes());
    }

    @Test
    void editingTheSettingsRescansAndRemovingTheDirectoryAsks(FxRobot robot) throws IOException {
        MenuItem removeDirectory = item(menu(robot, "Current project"), "Remove directory from project");
        assertTrue(removeDirectory.isDisable(), "no directory yet");
        addNotes(robot);
        assertFalse(removeDirectory.isDisable());
        directorySettingsAnswer = s -> new DirectorySettings(s.includes(), s.excludes(), false);
        fireContextItem(robot, notes, "Edit directory settings…");
        waitUntil(() -> project().filesOf(notes).size() == 2);
        assertTrue(project().definitionChangedProperty().get());

        fireContextItem(robot, notes, "Remove this directory from the project");
        waitUntil(() -> tree(robot).getRoot().getChildren().isEmpty());
        assertEquals(1, confirmations.size());
        assertTrue(project().getDefinitionDirectories().isEmpty());
        assertTrue(removeDirectory.isDisable());
    }
}
