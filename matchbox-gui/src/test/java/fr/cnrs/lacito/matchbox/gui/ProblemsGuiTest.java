package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.tag.TagGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.DiagnosticsPane;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemRow;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The Parsing errors and Semantic issues tabs: categories, fixes, All files, navigation, export. */
class ProblemsGuiTest extends MainWindowFixture {

    /** Line 2: a syntax error (list in the header); line 4: a reversed range; line 5: an empty tag. */
    static final String A = "---\n- item\n---\n@media a.wav 1:20-1:10\nx #\"\" y\n";
    static final String B = "first\n#\"\"\n";

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

    private DiagnosticsPane semantic() {
        return fileMode().semanticIssuesPane();
    }

    private DiagnosticsPane parsing() {
        return fileMode().parsingErrorsPane();
    }

    private InlineCssTextArea area(FxRobot robot) {
        return robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class);
    }

    private void grammars(FxRobot robot, GrammarPlugin... plugins) {
        robot.interact(() -> {
            FileType type = new FileType("All files", ".*\\..*");
            for (GrammarPlugin p : plugins) type.addGrammar(p);
            project().setFileTypes(List.of(type));
        });
    }

    private Path open(FxRobot robot, String name, String content) throws IOException {
        Path file = addFile(robot, name, content);
        waitUntil(() -> workbench().session().corpus().text(document(file).getId()).isPresent());
        showFile(robot, file);
        return file;
    }

    private Path setUp(FxRobot robot) throws IOException {
        grammars(robot, new MarkdownHeadingGrammarPlugin(), new MediaRefGrammarPlugin(), new TagGrammarPlugin());
        java.nio.file.Files.writeString(dir.resolve("a.wav"), ""); // the media file exists: no Media check issue
        Path a = open(robot, "a.md", A);
        waitUntil(() -> semantic().table().getItems().size() == 2);
        return a;
    }

    private static List<String> codes(DiagnosticsPane pane) {
        return pane.table().getItems().stream().map(r -> r.diagnostic().code()).toList();
    }

    @Test
    void semantic_issues_have_their_own_tab_and_the_margin_still_shows_everything(FxRobot robot) throws IOException {
        setUp(robot);
        assertEquals(List.of(MediaRefGrammarPlugin.CODE_RANGE_ORDER, TagGrammarPlugin.CODE_EMPTY), codes(semantic()));
        assertEquals(List.of(MarkdownHeadingGrammarPlugin.CODE_UNSUPPORTED), codes(parsing()));
        assertEquals("Semantic issues (2)", semantic().tab().getText());
        assertEquals("Parsing errors (1)", parsing().tab().getText());
        // the editor (margin, ruler) still has the three problems
        assertEquals(3, fileMode().getEditorController().getCurrentFileErrors().size());
        // existing ids kept for the parsing errors
        assertTrue(robot.lookup("#errorTableView").tryQuery().isPresent());
        assertTrue(robot.lookup("#semanticTableView").tryQuery().isPresent());

        // selecting a row selects its span in the editor
        robot.interact(() -> semantic().table().getSelectionModel().select(1));
        int tag = A.indexOf("#\"\"");
        waitUntil(() -> area(robot).getSelection().getStart() == tag);

        // the menu item selects the tab
        robot.interact(() -> fileMode().showSemanticIssues());
        assertTrue(semantic().tab().isSelected());
    }

    @Test
    void a_fix_is_one_undoable_change(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        ProblemRow range = semantic().table().getItems().get(0);
        Diagnostic.Fix fix = range.diagnostic().fixes().get(0);
        assertEquals("Swap start and end", fix.label());
        robot.interact(() -> workbench().problems().applyFix(range, fix));
        waitUntil(() -> document(a).getCurrentText().contains("1:10-1:20"));
        waitUntil(() -> semantic().table().getItems().size() == 1);
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(a).getCurrentText().equals(A));
        waitUntil(() -> semantic().table().getItems().size() == 2);
    }

    @Test
    void all_files_lists_the_project_and_enter_opens_the_file(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        Path b = addFile(robot, "b.md", B);
        waitUntil(() -> workbench().session().corpus().text(document(b).getId()).isPresent());
        assertEquals(2, semantic().table().getItems().size(), "the file shown only");

        CheckBox all = robot.lookup("#semanticAllFilesCheck").queryAs(CheckBox.class);
        robot.interact(() -> all.setSelected(true));
        waitUntil(() -> semantic().table().getItems().size() == 3);
        assertEquals("Semantic issues (3 in 2 files)", semantic().tab().getText());
        assertTrue(settings.editorProperty().get().problemsOrDefaults().semanticIssuesAllFiles());
        assertFalse(settings.editorProperty().get().problemsOrDefaults().parsingErrorsAllFiles());

        // selecting a row of another file does not open it
        ProblemRow inB = semantic().table().getItems().stream()
            .filter(r -> r.file().equals(document(b).getId())).findFirst().orElseThrow();
        robot.interact(() -> semantic().table().getSelectionModel().select(inB));
        assertEquals(document(a).getId(), workbench().focus().orElseThrow().text());
        // going to it does, and Back returns
        robot.interact(() -> workbench().problems().go(inB));
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(b).getId())).orElse(false));
        waitUntil(() -> area(robot).getSelection().getStart() == B.indexOf("#"));
        robot.interact(() -> workbench().navigator().back());
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(a).getId())).orElse(false));

        // the ◀ ▶ buttons go through the list, wrapping
        robot.interact(() -> semantic().table().getSelectionModel().clearSelection());
        robot.interact(() -> semantic().step(false));
        assertEquals(inB, semantic().table().getSelectionModel().getSelectedItem());
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(b).getId())).orElse(false));
    }

    @Test
    void f8_goes_from_problem_to_problem(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        Path b = addFile(robot, "b.md", B);
        waitUntil(() -> workbench().session().corpus().text(document(b).getId()).isPresent());
        robot.interact(() -> area(robot).moveTo(0));
        robot.interact(() -> fileMode().nextProblemInFile(true));
        waitUntil(() -> area(robot).getCurrentParagraph() == 1); // line 2: the syntax error
        robot.interact(() -> fileMode().nextProblemInFile(true));
        waitUntil(() -> area(robot).getCurrentParagraph() == 3); // line 4: the range
        robot.interact(() -> fileMode().nextProblemInFile(false));
        waitUntil(() -> area(robot).getCurrentParagraph() == 1);
        // across the project: from the last problem of a.md to b.md
        robot.interact(() -> area(robot).moveTo(A.indexOf("x #")));
        robot.interact(() -> fileMode().nextProblemInProject(true));
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(b).getId())).orElse(false));
        // a jump: Back returns to a.md
        robot.interact(() -> workbench().navigator().back());
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(a).getId())).orElse(false));
    }

    @Test
    void a_problem_moves_with_the_text_typed_since_the_parse(FxRobot robot) throws IOException {
        Path a = setUp(robot);
        ProblemRow tag = semantic().table().getItems().get(1);
        AtomicReference<Optional<Focus>> target = new AtomicReference<>();
        robot.interact(() -> {
            document(a).applyEdits(List.of(TextEdit.insert(0, "new line\n")), EditOrigin.of(EditOrigin.Kind.TYPING, this));
            // before the parse
            target.set(workbench().problems().target(tag));
        });
        assertEquals(tag.diagnostic().startIndex() + "new line\n".length(), target.get().orElseThrow().start());
    }

    @Test
    void export_writes_the_rows_shown(FxRobot robot) throws IOException {
        setUp(robot);
        Path out = Files.createTempFile("problems", ".csv");
        robot.interact(() -> {
            semantic().setExportChooser(name -> Optional.of(out));
            robot.lookup("#semanticExport").queryButton().fire();
        });
        String csv = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("File,Line,Position,Severity,Category,Plugin,Code,Object,Message\r\n"), csv);
        assertTrue(csv.contains("a.md,5,"), csv);
        assertTrue(csv.contains(TagGrammarPlugin.CODE_EMPTY), csv);
        Files.deleteIfExists(out);
    }

    @Test
    void hiding_warnings_is_remembered_per_tab(FxRobot robot) throws IOException {
        grammars(robot, new MarkdownHeadingGrammarPlugin());
        open(robot, "dup.md", "---\ndate: 1\ndate: 2\n---\n");
        waitUntil(() -> semantic().table().getItems().size() == 1);
        assertEquals("Semantic issues (1 warning)", semantic().tab().getText());
        CheckBox show = robot.lookup("#semanticShowWarningsCheck").queryAs(CheckBox.class);
        robot.interact(() -> show.setSelected(false));
        assertTrue(semantic().table().getItems().isEmpty());
        assertFalse(settings.editorProperty().get().problemsOrDefaults().showSemanticWarnings());
        assertTrue(settings.editorProperty().get().showWarnings(), "the other tab keeps its own setting");
        @SuppressWarnings("unchecked")
        TableView<ProblemRow> table = robot.lookup("#semanticTableView").queryAs(TableView.class);
        assertTrue(table.getItems().isEmpty());
    }
}
