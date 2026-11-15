package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.gui.mode.DictionaryHistoryMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.lexicon.history.EntryRow;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryRecorder;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult;
import fr.cnrs.lacito.matchbox.model.lexicon.history.OperationRow;
import fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/**
 * Dictionary commands in the notes: their objects, statuses and entry in File Mode, the two gates
 * of the history (nothing tried while a syntax or static error exists; stop at the first dynamic
 * error), the Dictionary History mode, and the order of the files.
 */
@Tag("gui")
class DictionaryHistoryGuiTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    /** A context dictionary with mami 'pig', set on the project. */
    private Path contextDictionary(FxRobot robot) throws Exception {
        LiftDictionary d = LiftDictionary.makeBuilder().withObjectLanguages("tww").withMetaLanguages("en").build();
        var b = d.getComponentBuilder();
        var mami = b.entry().withForm("tww", "mami").build();
        mami.setId("mami_1");
        b.sense(mami).withGloss("en", "pig").build();
        Path file = dir.resolve("context.lift");
        d.save(file.toFile());
        robot.interact(() -> project().setContextDictionary(file));
        return file;
    }

    private HistoryResult history() {
        return workbench().history().result();
    }

    private void waitForHistory(java.util.function.Predicate<HistoryResult> condition) {
        waitUntil(() -> !workbench().history().computingProperty().get() && condition.test(history()));
    }

    /** The objects table listing the command whose text starts with {@code prefix} (one tab per kind). */
    @SuppressWarnings("unchecked")
    private TableView<CorpusObject> commandTable(FxRobot robot, String prefix) {
        java.util.function.Predicate<TableView> lists = t -> t.getItems().stream().anyMatch(i ->
            i instanceof LexiconMutationCommand c && c.sourceText().startsWith(prefix));
        waitUntil(() -> robot.lookup(".table-view").queryAllAs(TableView.class).stream().anyMatch(lists));
        return robot.lookup(".table-view").queryAllAs(TableView.class).stream().filter(lists).findFirst().orElseThrow();
    }

    private static String cell(TableView<CorpusObject> table, String column, int row) {
        TableColumn<CorpusObject, ?> c = table.getColumns().stream().filter(col -> column.equals(col.getText()))
            .findFirst().orElseThrow();
        return String.valueOf(c.getCellData(row));
    }

    private DictionaryHistoryMode mode() {
        return (DictionaryHistoryMode) workbench().modes().orElseThrow().get(DictionaryHistoryMode.ID);
    }

    private void switchTo(FxRobot robot, String modeId) {
        robot.interact(() -> workbench().navigator().switchTo(modeId));
    }

    @SuppressWarnings("unchecked")
    private TableView<EntryRow> entries(FxRobot robot) {
        return robot.lookup("#historyEntries").queryAs(TableView.class);
    }

    @SuppressWarnings("unchecked")
    private TableView<OperationRow> commands(FxRobot robot) {
        return robot.lookup("#historyCommands").queryAs(TableView.class);
    }

    private void replaceAll(FxRobot robot, Path file, String content) {
        var d = document(file);
        robot.interact(() -> assertTrue(d.applyEdits(List.of(TextEdit.replace(d.getCurrentText(), 0,
            d.getCurrentText().length(), content)), EditOrigin.of(EditOrigin.Kind.COMMAND, this))));
    }

    @Test
    void fileModeShowsTheCommandsTheirStatusAndTheEntryAfterThem(FxRobot robot) throws Exception {
        contextDictionary(robot);
        Path notes = addFile(robot, "notes.txt", "Monday\nc e(\"lofi\", s(\"water\"))\np /mami/pig\n");
        showFile(robot, notes);
        waitForHistory(h -> h.isClean() && h.commandCount() == 2);
        // one tab per kind: the create commands, and the block p /mami/pig expands into
        TableView<CorpusObject> table = commandTable(robot, "c e(");
        waitUntil(() -> cell(table, "Status", 0).contains("Applied"));
        assertEquals("● Applied", cell(table, "Status", 0));
        assertEquals("lofi", cell(table, "Entry", 0));
        TableView<CorpusObject> block = commandTable(robot, "p /mami");
        assertEquals("● No change", cell(block, "Status", 0), "p /mami/pig finds the sense");
        assertEquals("mami", cell(block, "Entry", 0));

        robot.interact(() -> table.getSelectionModel().select(0));
        waitUntil(() -> robot.lookup("#commandView").tryQuery().isPresent());
        try {
            waitUntil(() -> robot.lookup("#entrySnapshotEditor").tryQuery().isPresent());
        } catch (AssertionError e) {
            throw new AssertionError("no entry editor; the detail shows: " + robot.lookup("#commandView .label")
                .queryAllAs(Label.class).stream().map(Label::getText).toList(), e);
        }
        Node editor = robot.lookup("#entrySnapshotEditor").query();
        var fields = robot.from(editor).lookup((Node n) -> n instanceof TextInputControl).queryAllAs(TextInputControl.class);
        assertFalse(fields.isEmpty(), "the entry is shown");
        assertTrue(fields.stream().noneMatch(TextInputControl::isEditable), "read-only");

        // the link to the history of the entry opens the mode on it
        Hyperlink link = robot.lookup("#commandView .hyperlink").match((Node n) -> n instanceof Hyperlink h
            && h.getText().startsWith("Show history of")).queryAs(Hyperlink.class);
        assertEquals("Show history of lofi", link.getText());
        robot.interact(link::fire);
        waitUntil(() -> workbench().modes().orElseThrow().activeId().equals(Optional.of(DictionaryHistoryMode.ID)));
        waitUntil(() -> entries(robot).getSelectionModel().getSelectedItem() != null
            && entries(robot).getSelectionModel().getSelectedItem().form("tww").equals("lofi"));
    }

    @Test
    void gateTwoADynamicErrorStopsTheHistoryAndIsASemanticIssue(FxRobot robot) throws Exception {
        contextDictionary(robot);
        Path notes = addFile(robot, "a.txt", "c e(\"lofi\", s(\"water\"))\nc /mami s(\"pig\")\n");
        waitForHistory(h -> h.status() instanceof HistoryResult.Status.StoppedAt);
        var text = workbench().session().corpus().text(document(notes).getId()).orElseThrow();
        List<Diagnostic> found = workbench().problems().diagnosticsOf(text).stream()
            .filter(d -> HistoryRecorder.CHECK_ID.equals(d.grammarId())).toList();
        assertEquals(1, found.size());
        assertEquals(2, found.get(0).line());
        assertEquals(Diagnostic.Severity.ERROR, found.get(0).severity());

        replaceAll(robot, notes, "c e(\"lofi\", s(\"water\"))\nc /mami s(\"boar\")\n");
        waitForHistory(HistoryResult::isClean);
        var fixed = workbench().session().corpus().text(document(notes).getId()).orElseThrow();
        assertTrue(workbench().problems().diagnosticsOf(fixed).stream()
            .noneMatch(d -> HistoryRecorder.CHECK_ID.equals(d.grammarId())));
    }

    @Test
    void gateOneASyntaxErrorMeansNothingIsTriedAndTheBannerSaysSo(FxRobot robot) throws Exception {
        contextDictionary(robot);
        addFile(robot, "a.txt", "c e(\"lofi\", s(\"water\"))\n");
        addFile(robot, "b.txt", "c e(\"mafa\", s(\"sun\")\n");
        waitForHistory(h -> h.status() instanceof HistoryResult.Status.NotTried);
        switchTo(robot, DictionaryHistoryMode.ID);
        waitUntil(() -> robot.lookup("#historyBanner").queryAs(javafx.scene.layout.HBox.class).isVisible());
        Label banner = robot.lookup("#historyBanner .label").queryAs(Label.class);
        assertTrue(banner.getText().startsWith("Commands not applied: 1 syntax or static error"), banner.getText());
        assertEquals(List.of("mami"), entries(robot).getItems().stream().map(e -> e.form("tww")).toList(),
            "the context dictionary, unchanged");
    }

    @Test
    void theModeShowsEntriesTheirCommandsAndTheirPastStates(FxRobot robot) throws Exception {
        contextDictionary(robot);
        Path notes = addFile(robot, "notes.txt",
            "c e(\"lofi\", s(\"water\"))\ns /lofi/water (d@en = \"Fresh water\")\n");
        waitForHistory(h -> h.isClean() && h.commandCount() == 2);
        switchTo(robot, DictionaryHistoryMode.ID);
        waitUntil(() -> entries(robot).getItems().size() == 2);
        EntryRow lofi = entries(robot).getItems().stream().filter(e -> e.form("tww").equals("lofi")).findFirst().orElseThrow();
        robot.interact(() -> entries(robot).getSelectionModel().select(lofi));
        waitUntil(() -> commands(robot).getItems().size() == 2);
        waitUntil(() -> robot.lookup("#entrySnapshotEditor").tryQuery().isPresent());

        // a command selected: the entry as it was after it
        robot.interact(() -> commands(robot).getSelectionModel().select(0));
        Node bar = robot.lookup("#historyTimeTravel").query();
        waitUntil(bar::isVisible);
        robot.interact(() -> commands(robot).getSelectionModel().clearSelection());
        waitUntil(() -> !bar.isVisible());

        // the filter
        robot.interact(() -> robot.lookup("#historyFilter").queryAs(javafx.scene.control.TextField.class).setText("MAM"));
        waitUntil(() -> entries(robot).getItems().size() == 1);
        robot.interact(() -> robot.lookup("#historyFilter").queryAs(javafx.scene.control.TextField.class).clear());
        waitUntil(() -> entries(robot).getItems().size() == 2);

        // Enter on a command: File Mode at its line; Back returns
        robot.interact(() -> {
            entries(robot).getSelectionModel().select(lofi);
            commands(robot).requestFocus();
            commands(robot).getSelectionModel().select(1);
        });
        robot.type(KeyCode.ENTER);
        waitUntil(() -> workbench().modes().orElseThrow().activeId().equals(Optional.of(Modes.FILE)));
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(notes).getId())).orElse(false));
        robot.interact(() -> workbench().navigator().back());
        waitUntil(() -> workbench().modes().orElseThrow().activeId().equals(Optional.of(DictionaryHistoryMode.ID)));

        // the panels
        robot.interact(() -> robot.lookup("#historyEntriesToggle").queryAs(javafx.scene.control.ToggleButton.class).fire());
        assertFalse(mode().layoutProperty().get().isShown(0));
        robot.interact(() -> robot.lookup("#historyEntriesToggle").queryAs(javafx.scene.control.ToggleButton.class).fire());
        assertTrue(mode().layoutProperty().get().isShown(0));
    }

    @Test
    void withoutCommandNorDictionaryTheModeSaysWhatToDo(FxRobot robot) throws Exception {
        addFile(robot, "notes.txt", "Just notes.\n");
        switchTo(robot, DictionaryHistoryMode.ID);
        waitUntil(() -> entries(robot).getPlaceholder() instanceof Label l && l.getText().startsWith("No dictionary history"));
    }

    @Test
    void byModificationDateTheFilesAreAFlatListInHistoryOrder(FxRobot robot) throws Exception {
        Path a = addFile(robot, "a.txt", "c e(\"lofi\")\n");
        Path b = addFile(robot, "b.txt", "c e(\"mafa\")\n");
        Files.setLastModifiedTime(a, FileTime.from(Instant.parse("2025-02-01T00:00:00Z")));
        Files.setLastModifiedTime(b, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));
        waitForHistory(h -> h.commandCount() == 2);
        assertEquals(List.of(a, b), workbench().history().orderedTexts().stream().map(t -> t.path()).toList(), "by path");
        robot.interact(() -> project().setFileOrder(ProjectFileOrder.MODIFIED));
        waitUntil(() -> workbench().history().orderedTexts().stream().map(t -> t.path()).toList().equals(List.of(b, a)));
        @SuppressWarnings("unchecked")
        TreeView<FileTree.Node> tree = robot.lookup("#fileTreeView").queryAs(TreeView.class);
        waitUntil(() -> tree.getRoot().getChildren().size() == 2
            && tree.getRoot().getChildren().get(0).getValue().path().equals(b));
        assertTrue(tree.getRoot().getChildren().stream().allMatch(i -> i.getValue() instanceof FileTree.FileNode));
        waitUntil(() -> robot.lookup("#fileTreeView .tree-cell").queryAllAs(javafx.scene.control.TreeCell.class).stream()
            .anyMatch(c -> "1 · b.txt".equals(c.getText())));
    }
}
