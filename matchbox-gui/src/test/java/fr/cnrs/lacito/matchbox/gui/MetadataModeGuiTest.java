package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.grammar.igt.IgtGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.mode.MetadataMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.ColumnFilter;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.FilterHeader;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.MetadataRow;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.TableExport;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The Metadata mode on the real window: rows, columns, filters, edits, navigation, bulk edits. */
class MetadataModeGuiTest extends MainWindowFixture {

    static final String NOTES = """
        ---
        speaker: Ann
        ---

        # Session 1

        ---
        speaker: Bob
        Media: a.wav
        ---

        :::{id="1", media="b.wav"}
        a b
        c d
        :::

        # Session 2

        prose
        """;

    /** Answers of the dialogs: texts to give, confirmations; what was asked. */
    private final LinkedList<String> answers = new LinkedList<>();
    private boolean confirmAnswer = true;
    private final List<String> asked = new ArrayList<>();

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private MetadataMode mode() {
        return (MetadataMode) workbench().modes().orElseThrow().get(MetadataMode.ID);
    }

    @SuppressWarnings("unchecked")
    private TreeTableView<MetadataMode.Item> table(FxRobot robot) {
        return robot.lookup("#metadataTable").queryAs(TreeTableView.class);
    }

    private void grammars(FxRobot robot, GrammarPlugin... plugins) {
        robot.interact(() -> {
            FileType type = new FileType("All files", ".*\\..*");
            for (GrammarPlugin p : plugins) type.addGrammar(p);
            project().setFileTypes(List.of(type));
        });
    }

    private Path setUp(FxRobot robot) throws IOException {
        grammars(robot, new MarkdownHeadingGrammarPlugin(), new IgtGrammarPlugin(), new MediaRefGrammarPlugin());
        Path a = addFile(robot, "a.md", NOTES);
        addFile(robot, "b.md", "# Only a title\n");
        waitUntil(() -> workbench().session().corpus().texts().size() == 2);
        robot.interact(() -> workbench().modes().orElseThrow().activate(MetadataMode.ID));
        robot.interact(() -> mode().setDialogs(new MetadataMode.Dialogs() {
            public Optional<String> ask(String title, String header, String initial) {
                asked.add(header);
                return Optional.ofNullable(answers.poll());
            }
            public boolean confirm(String title, String header, String content) {
                asked.add(header);
                return confirmAnswer;
            }
            public void inform(String title, String header, String content) {
                asked.add(header + ": " + content);
            }
            public Optional<Path> exportFile(Optional<Path> directory) {
                return Optional.empty();
            }
        }));
        waitUntil(() -> rows(robot).size() == 6);
        return a;
    }

    private List<MetadataRow> rows(FxRobot robot) {
        List<MetadataRow> rows = new ArrayList<>();
        var t = table(robot);
        for (int i = 0; i < t.getExpandedItemCount(); i++) {
            TreeItem<MetadataMode.Item> item = t.getTreeItem(i);
            if (item != null && item.getValue() instanceof MetadataMode.RowItem r) rows.add(r.row());
        }
        return rows;
    }

    private List<String> columnTitles(FxRobot robot) {
        return table(robot).getColumns().stream().map(c -> (String) c.getUserData())
            .map(id -> id.startsWith("\0") ? id.substring(1) : id).toList();
    }

    private TreeTableColumn<MetadataMode.Item, ?> column(FxRobot robot, String id) {
        return table(robot).getColumns().stream().filter(c -> id.equals(c.getUserData())).findFirst().orElseThrow();
    }

    private MetadataRow row(FxRobot robot, String type, String label) {
        return rows(robot).stream().filter(r -> r.type().equals(type) && r.label().equals(label)).findFirst().orElseThrow();
    }

    private void selectCell(FxRobot robot, MetadataRow row, String key) {
        robot.interact(() -> {
            var t = table(robot);
            int index = -1;
            for (int i = 0; i < t.getExpandedItemCount(); i++) {
                if (t.getTreeItem(i).getValue() instanceof MetadataMode.RowItem r && r.row().key().equals(row.key())) index = i;
            }
            t.getSelectionModel().clearAndSelect(index, column(robot, key));
            t.getFocusModel().focus(index, column(robot, key));
            t.requestFocus();
        });
    }

    private FilterHeader header(FxRobot robot, String id) {
        return robot.lookup("." + FilterHeader.STYLE_CLASS).match((FilterHeader h) -> id.equals(h.getUserData())).query();
    }

    @Test
    void rowsColumnsAndCounts(FxRobot robot) throws IOException {
        setUp(robot);
        assertEquals(List.of("file", "type", "object", "line", "speaker", "Media", "id", "media"), columnTitles(robot));
        assertEquals(List.of("Header", "Section", "IGT", "Section", "Header", "Section"),
            rows(robot).stream().map(MetadataRow::type).toList());
        assertEquals("Objects without metadata (3)", robot.lookup("#metadataWithout").queryAs(CheckBox.class).getText());
        assertTrue(header(robot, "Media").getChildren().get(0).toString().contains("⚠"), "case variant marked");
        robot.interact(() -> robot.lookup("#metadataWithout").queryAs(CheckBox.class).setSelected(false));
        waitUntil(() -> rows(robot).size() == 3);
    }

    @Test
    void filtersAndSearch(FxRobot robot) throws IOException {
        setUp(robot);
        robot.interact(() -> header(robot, "speaker").set(ColumnFilter.NONE.withText("bob")));
        waitUntil(() -> rows(robot).size() == 1);
        robot.interact(() -> header(robot, "speaker").set(ColumnFilter.NONE.withCells(ColumnFilter.Cells.EMPTY)));
        waitUntil(() -> rows(robot).size() == 4);
        robot.interact(() -> header(robot, "speaker").set(ColumnFilter.NONE.withText("(").withMatch(ColumnFilter.Match.REGEX)));
        waitUntil(() -> rows(robot).size() == 6);
        assertTrue(header(robot, "speaker").field().getStyle().contains("#c33"), "invalid expression shown");
        robot.interact(() -> header(robot, "speaker").set(ColumnFilter.NONE));
        robot.interact(() -> robot.lookup("#metadataSearch").queryAs(javafx.scene.control.TextField.class).setText("b.wav"));
        waitUntil(() -> rows(robot).size() == 1);
        assertEquals("IGT", rows(robot).get(0).type());
        robot.interact(() -> mode().table().getScene().getWindow().requestFocus());
        robot.interact(() -> {
            robot.lookup("#metadataSearch").queryAs(javafx.scene.control.TextField.class).clear();
        });
        waitUntil(() -> rows(robot).size() == 6);
    }

    @Test
    void editingACellWritesTheTextAndUndoRestoresIt(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        MetadataRow header = row(robot, "Header", "—");
        robot.interact(() -> mode().write(header, "speaker", "Carl", false));
        waitUntil(() -> document(a).getCurrentText().contains("speaker: Carl"));
        waitUntil(() -> row(robot, "Header", "—").value("speaker").equals(Optional.of("Carl")));
        assertTrue(workbench().journal().undoLabelProperty().get().contains("Set speaker of the header"),
            workbench().journal().undoLabelProperty().get());
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(a).getCurrentText().equals(NOTES));
        waitUntil(() -> row(robot, "Header", "—").value("speaker").equals(Optional.of("Ann")));

        // typing in a cell (key events): Session 2 has no block, the first value creates it
        MetadataRow session2 = row(robot, "Section", "Session 2");
        selectCell(robot, session2, "speaker");
        robot.write("Dan");
        robot.type(KeyCode.ENTER);
        waitUntil(() -> document(a).getCurrentText().contains("# Session 2\n---\nspeaker: Dan\n---\n"));
        waitUntil(() -> !row(robot, "Section", "Session 2").withoutBlock());

        // clearing removes the entry
        robot.interact(() -> mode().write(row(robot, "Section", "Session 2"), "speaker", "", false));
        waitUntil(() -> !document(a).getCurrentText().contains("speaker: Dan"));

        // an IGT: adds to its block
        robot.interact(() -> mode().write(row(robot, "IGT", "1"), "speaker", "Eve", false));
        waitUntil(() -> document(a).getCurrentText().contains("media=\"b.wav\", speaker=\"Eve\"}"));
    }

    @Test
    void aFileWithoutHeaderGetsOne(FxRobot robot) throws IOException {
        setUp(robot);
        Path b = dir.resolve("b.md");
        MetadataRow candidate = rows(robot).stream().filter(MetadataRow::isHeaderCandidate).findFirst().orElseThrow();
        robot.interact(() -> mode().write(candidate, "speaker", "Fay", false));
        waitUntil(() -> document(b).getCurrentText().equals("---\nspeaker: Fay\n---\n# Only a title\n"));
        waitUntil(() -> rows(robot).stream().noneMatch(MetadataRow::isHeaderCandidate));
    }

    @Test
    void readOnlyAndRefusedEditsAreSaid(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        robot.interact(() -> mode().write(row(robot, "Header", "—"), "bad:key", "x", false));
        waitUntil(() -> workbench().status().messageProperty().get().startsWith("Not changed"));
        assertEquals(NOTES, document(a).getCurrentText());
    }

    @Test
    void showInEditorAndBack(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        selectCell(robot, row(robot, "Section", "Session 1"), "speaker");
        robot.press(KeyCode.SHORTCUT, KeyCode.E).release(KeyCode.E, KeyCode.SHORTCUT);
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(Modes.FILE));
        int bob = NOTES.indexOf("Bob");
        waitUntil(() -> workbench().focus().map(f -> f.start() == bob).orElse(false));
        robot.interact(() -> workbench().navigator().back());
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(MetadataMode.ID));
        assertTrue(document(a) != null);
    }

    @Test
    void columnsMoveWithTheKeyboardAndCanBeHidden(FxRobot robot) throws IOException {
        setUp(robot);
        selectCell(robot, row(robot, "Header", "—"), "speaker");
        robot.press(KeyCode.ALT, KeyCode.SHIFT, KeyCode.RIGHT).release(KeyCode.RIGHT, KeyCode.SHIFT, KeyCode.ALT);
        waitUntil(() -> columnTitles(robot).subList(4, 6).equals(List.of("Media", "speaker")));
        assertEquals(List.of("file", "type", "object", "line"), columnTitles(robot).subList(0, 4));
    }

    @Test
    void fillDownIsOneUndoEntryAndRenameIsPreviewed(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        robot.interact(() -> {
            var t = table(robot);
            t.getSelectionModel().clearSelection();
            var speaker = column(robot, "speaker");
            for (int i = 0; i < 4; i++) t.getSelectionModel().select(i, speaker);
        });
        robot.interact(() -> mode().fillDown());
        waitUntil(() -> document(a).getCurrentText().contains("speaker=\"Ann\"")
            && document(a).getCurrentText().contains("# Session 2\n---\nspeaker: Ann\n---"));
        assertTrue(workbench().journal().undoLabelProperty().get().startsWith("Set speaker in 3 blocks"),
            workbench().journal().undoLabelProperty().get());
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(a).getCurrentText().equals(NOTES));

        answers.add("talker");
        robot.interact(() -> mode().renameKey("speaker"));
        waitUntil(() -> document(a).getCurrentText().contains("talker: Ann") && document(a).getCurrentText().contains("talker: Bob"));
        assertTrue(asked.stream().anyMatch(q -> q.startsWith("Renames speaker to talker in 2 blocks of 1 file")), asked.toString());
        waitUntil(() -> columnTitles(robot).contains("talker"));
    }

    @Test
    void inheritedValuesProblemsGroupsAndExport(FxRobot robot) throws IOException {
        setUp(robot);
        robot.interact(() -> robot.lookup("#metadataInherited").queryAs(CheckBox.class).setSelected(true));
        waitUntil(() -> {
            Object v = column(robot, "speaker").getCellData(2);
            return "Bob".equals(v);   // the IGT inherits the speaker of Session 1
        });
        // the media check outlines the missing b.wav of the IGT
        String csv = mode().exportText(TableExport.Format.CSV_COMMA);
        assertTrue(csv.startsWith("File,Type,Object,Line,speaker,Media,id,media\r\n"), csv);
        assertTrue(csv.contains("a.md,IGT,1,"), csv);
        robot.interact(() -> robot.lookup("#metadataGroup").queryAs(ChoiceBox.class).setValue("Group by file"));
        waitUntil(() -> table(robot).getRoot().getChildren().size() == 2);
        assertEquals(6, rows(robot).size());
        robot.interact(() -> table(robot).getRoot().getChildren().get(0).setExpanded(false));
        waitUntil(() -> rows(robot).size() == 2);
    }

    @Test
    void revealSelectsTheBlockAtTheCaret(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        robot.interact(() -> workbench().modes().orElseThrow().activate(Modes.FILE));
        showFile(robot, a);
        int inIgt = NOTES.indexOf("a b");
        robot.interact(() -> {
            var area = robot.lookup("#fileContentCodeArea").queryAs(org.fxmisc.richtext.InlineCssTextArea.class);
            area.moveTo(inIgt);
        });
        robot.press(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.M).release(KeyCode.M, KeyCode.SHIFT, KeyCode.SHORTCUT);
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(MetadataMode.ID));
        waitUntil(() -> table(robot).getSelectionModel().getSelectedItem() != null
            && table(robot).getSelectionModel().getSelectedItem().getValue() instanceof MetadataMode.RowItem r
            && r.row().type().equals("IGT"));
    }

    @Test
    void columnSettingsAreKeptPerProject(FxRobot robot) throws IOException {
        setUp(robot);
        robot.interact(() -> project().setFile(dir.resolve("p.mbx").toFile()));
        selectCell(robot, row(robot, "Header", "—"), "speaker");
        robot.press(KeyCode.ALT, KeyCode.SHIFT, KeyCode.RIGHT).release(KeyCode.RIGHT, KeyCode.SHIFT, KeyCode.ALT);
        waitUntil(() -> settings.metadataModeProperty().get().columnsOf(dir.resolve("p.mbx").toFile().getAbsolutePath())
            .order().equals(List.of("Media", "speaker", "id", "media")));
        robot.interact(() -> header(robot, "speaker").set(ColumnFilter.NONE.withText("x")));
        assertEquals(List.of("Media", "speaker", "id", "media"),
            settings.metadataModeProperty().get().columnsOf(dir.resolve("p.mbx").toFile().getAbsolutePath()).order(),
            "filters are not part of the settings");
        assertTrue(Files.exists(dir.resolve("a.md")));
    }

    @Test
    void completionOffersTheValuesOfTheColumnWithoutTakingTheKeyboard(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        selectCell(robot, row(robot, "Section", "Session 2"), "speaker");
        robot.write("B");
        waitUntil(() -> robot.lookup("#metadataCompletions").tryQuery().map(n -> n.isVisible()).orElse(false));
        @SuppressWarnings("unchecked")
        var list = (javafx.scene.control.ListView<String>) robot.lookup("#metadataCompletions").query();
        assertEquals(List.of("Bob"), List.copyOf(list.getItems()));
        robot.type(KeyCode.DOWN);
        robot.type(KeyCode.ENTER);   // takes the suggestion
        robot.type(KeyCode.ENTER);   // writes it
        waitUntil(() -> document(a).getCurrentText().contains("# Session 2\n---\nspeaker: Bob\n---\n"));
    }
}
