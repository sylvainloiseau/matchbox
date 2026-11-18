package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** Current project › Create file… and the file tree's "New file here…". */
class CreateFileGuiTest extends MainWindowFixture {

    private final List<String> errors = new ArrayList<>();

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
        ErrorReporter.setDisplay((owner, title, message) -> errors.add(title + " | " + message));
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private Menu currentProject(FxRobot robot) {
        return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> "Current project".equals(m.getText())).findFirst().orElseThrow();
    }

    private List<String> itemTexts(Menu menu) {
        return menu.getItems().stream().map(MenuItem::getText).filter(t -> t != null)
            .map(t -> t.replace("_", "")).toList();
    }

    private void createFile(FxRobot robot) {
        MenuItem item = currentProject(robot).getItems().stream()
            .filter(i -> "_Create file…".equals(i.getText())).findFirst().orElseThrow();
        robot.interact(item::fire);
    }

    private Optional<TextDocument> documentOf(Path file) {
        Path f = file.toAbsolutePath().normalize();
        return project().getDerivedFileList().stream().filter(d -> d.getFilePath().equals(f)).findFirst();
    }

    private boolean displayed(Path file) {
        return workbench().focus().map(f -> documentOf(file).map(d -> d.getId().equals(f.text())).orElse(false))
            .orElse(false);
    }

    private String status() {
        return workbench().status().messageProperty().get();
    }

    @Test
    void theItemIsBelowAddFile(FxRobot robot) {
        List<String> texts = itemTexts(currentProject(robot));
        assertEquals(texts.indexOf("Add file…") + 1, texts.indexOf("Create file…"));
        MenuItem item = currentProject(robot).getItems().stream()
            .filter(i -> "_Create file…".equals(i.getText())).findFirst().orElseThrow();
        assertFalse(item.isDisable());
        assertNotNull(item.getAccelerator());
        assertEquals("N", item.getAccelerator().getName().replaceAll(".*\\+", ""));
    }

    @Test
    void createsAnEmptyFileAndOpensIt(FxRobot robot) throws IOException {
        Path file = dir.resolve("new.txt");
        chosenNewFile = file.toFile();
        createFile(robot);
        waitUntil(() -> displayed(file));
        assertTrue(Files.isRegularFile(file));
        assertEquals(0, Files.size(file));
        assertTrue(project().getDefinitionPaths().contains(file.toAbsolutePath().normalize()));
        assertNotNull(treeCell(robot, file.toAbsolutePath().normalize()));
        assertEquals("Created new.txt in the project", status());
        assertEquals(0, workbench().focus().orElseThrow().start());
        // the proposed name: untitled.txt; untitled-2.txt once it exists
        assertTrue(newFileDialogs.get(0).endsWith(" | untitled.txt"), newFileDialogs.toString());

        // typing goes into the new file; Save current file writes it
        robot.clickOn("#fileContentCodeArea");
        robot.write("hello");
        waitUntil(() -> documentOf(file).orElseThrow().getCurrentText().equals("hello"));
        robot.press(KeyCode.SHORTCUT, KeyCode.S).release(KeyCode.S, KeyCode.SHORTCUT);
        waitUntil(() -> {
            try {
                return Files.readString(file, StandardCharsets.UTF_8).equals("hello");
            } catch (IOException e) {
                return false;
            }
        });
    }

    @Test
    void theDefaultNameIsTheFirstFreeOne(FxRobot robot) throws IOException {
        Files.writeString(dir.resolve("untitled.txt"), "");
        robot.interact(() -> project().setFile(dir.resolve("p.mbx").toFile()));
        chosenNewFile = null; // cancelled
        createFile(robot);
        assertEquals(List.of(dir.toAbsolutePath().normalize() + " | untitled-2.txt"), newFileDialogs);
        assertTrue(project().getDerivedFileList().isEmpty(), "cancel: nothing added");
    }

    @Test
    void anExistingFileOfTheProjectIsShownNotWritten(FxRobot robot) throws IOException {
        Path file = addFile(robot, "old.txt", "keep me");
        chosenNewFile = file.toFile();
        createFile(robot);
        waitUntil(() -> displayed(file));
        assertEquals("keep me", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("old.txt is already in the project", status());
        assertEquals(1, project().getDerivedFileList().size());
        assertEquals(List.of(), errors);
    }

    @Test
    void anExistingFileOutsideTheProjectIsRefused(FxRobot robot) throws IOException {
        Path file = Files.writeString(dir.resolve("other.txt"), "keep me");
        chosenNewFile = file.toFile();
        createFile(robot);
        waitUntil(() -> !errors.isEmpty());
        assertTrue(errors.get(0).startsWith("Error creating the file | other.txt already exists"), errors.toString());
        assertTrue(errors.get(0).contains("Add file…"));
        assertEquals("keep me", Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(project().getDerivedFileList().isEmpty());
    }

    @Test
    void aFileNoFileTypeMatchesIsReported(FxRobot robot) {
        Path file = dir.resolve("notes");
        chosenNewFile = file.toFile();
        createFile(robot);
        waitUntil(() -> displayed(file));
        assertTrue(status().contains("no file type matches it"), status());
        assertEquals("File types…", workbench().status().linkTextProperty().get());
    }

    @Test
    void aFileOutsideTheProjectDirectoryIsReported(FxRobot robot) throws IOException {
        Path projectDir = Files.createDirectories(dir.resolve("project"));
        robot.interact(() -> project().setFile(projectDir.resolve("p.mbx").toFile()));
        Path file = dir.resolve("outside.txt");
        chosenNewFile = file.toFile();
        createFile(robot);
        waitUntil(() -> displayed(file));
        assertEquals("Created outside.txt — it is not in the project directory", status());
        assertTrue(workbench().status().warningProperty().get());
    }

    @Test
    void aFileOfAProjectDirectoryIsListedUnderIt(FxRobot robot) throws IOException {
        Path notes = Files.createDirectories(dir.resolve("notes"));
        Files.createDirectories(notes.resolve("2024"));
        Files.writeString(notes.resolve("a.txt"), "a\n");
        Files.writeString(notes.resolve("2024/day1.txt"), "d\n"); // a sub-directory without file is hidden
        robot.interact(() -> {
            try {
                project().addDirectory(notes, new DirectorySettings(List.of("*.txt"), List.of(), true));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        waitUntil(() -> project().filesOf(notes).size() == 2);
        // New file here… on the sub-directory opens the dialog there
        Path file = notes.resolve("2024/day2.txt");
        chosenNewFile = file.toFile();
        TreeCell<?> cell = (TreeCell<?>) treeCell(robot, notes.resolve("2024"));
        MenuItem here = cell.getContextMenu().getItems().stream()
            .filter(i -> "New file here…".equals(i.getText())).findFirst().orElseThrow();
        robot.interact(here::fire);
        waitUntil(() -> displayed(file));
        assertEquals(notes.resolve("2024") + " | untitled.txt", newFileDialogs.get(0));
        assertEquals(List.of(notes), project().getDefinitionPaths(), "no <file> entry");
        assertEquals(List.of(notes.resolve("2024/day1.txt"), notes.resolve("2024/day2.txt"), notes.resolve("a.txt")),
            project().filesOf(notes));
        assertNotNull(treeCell(robot, file));
        assertTrue(robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class).getText().isEmpty());
    }
}
