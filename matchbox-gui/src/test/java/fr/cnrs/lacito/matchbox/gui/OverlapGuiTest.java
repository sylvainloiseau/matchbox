package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.tag.TagGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.OverlapRows;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.SectionBand;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** Overlapping readings in File Mode: highlighting, click, warnings, Overlaps tab, section band. */
class OverlapGuiTest extends MainWindowFixture {

    /** A section with prose, an IGT, more prose. */
    static final String NOTES = """
        # Story

        Some prose.

        :::
        a mio-jaflo-m-wi-j
        1sg down-canoe-top-m.sg-pred
        'I went down in canoe'
        :::

        More prose.
        """;

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

    private EditorController editor() {
        return fileMode().getEditorController();
    }

    private InlineCssTextArea area(FxRobot robot) {
        return robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class);
    }

    /** One file type for every file, with these grammars in this order. */
    private void grammars(FxRobot robot, GrammarPlugin... plugins) {
        robot.interact(() -> {
            FileType type = new FileType("All files", ".*\\..*");
            for (GrammarPlugin p : plugins) type.addGrammar(p);
            project().setFileTypes(List.of(type));
        });
    }

    private Text text(Path file) {
        return workbench().session().corpus().text(document(file).getId()).orElseThrow();
    }

    private Path open(FxRobot robot, String name, String content) throws IOException {
        Path file = addFile(robot, name, content);
        waitUntil(() -> workbench().session().corpus().text(document(file).getId()).isPresent());
        showFile(robot, file);
        return file;
    }

    private InterlinearSentence igt(Path file) {
        return text(file).objectsOf(InterlinearSentence.class).get(0);
    }

    @Test
    void the_igt_keeps_its_colour_inside_a_section_whatever_the_order(FxRobot robot) throws IOException {
        for (boolean markdownFirst : new boolean[] {true, false}) {
            if (markdownFirst) {
                grammars(robot, new MarkdownHeadingGrammarPlugin(), new NotebookGrammarPlugin());
            } else {
                grammars(robot, new NotebookGrammarPlugin(), new MarkdownHeadingGrammarPlugin());
            }
            Path file = open(robot, "story-" + markdownFirst + ".md", NOTES);
            InterlinearSentence igt = igt(file);
            String colour = workbench().highlightStyles().styleFor(text(file), igt).backgroundColor();
            assertNotNull(colour);
            int inIgt = igt.getTextCoordinate().startIndex() + 6;
            waitUntil(() -> area(robot).getStyleOfChar(inIgt).contains(colour));
            // the section colours its heading only: the prose has no background
            Section section = text(file).sections().get(0);
            String sectionColour = workbench().highlightStyles().styleFor(text(file), section).backgroundColor();
            int prose = NOTES.indexOf("Some prose") + 2;
            assertFalse(area(robot).getStyleOfChar(prose).contains("background"), area(robot).getStyleOfChar(prose));
            if (sectionColour != null) {
                assertFalse(area(robot).getStyleOfChar(inIgt).contains(sectionColour));
            }
        }
    }

    @Test
    void a_click_in_the_igt_selects_it_and_the_band_follows_the_section(FxRobot robot) throws IOException {
        grammars(robot, new MarkdownHeadingGrammarPlugin(), new NotebookGrammarPlugin());
        Path file = open(robot, "click.md", NOTES);
        InterlinearSentence igt = igt(file);
        int offset = igt.getTextCoordinate().startIndex() + 1;
        robot.interact(() -> {
            area(robot).moveTo(offset);
            Event.fireEvent(area(robot), new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
        });
        CorpusObject selected = editor().selectedObjectProperty().get();
        assertTrue(selected == igt || igt.getTextCoordinate().encloses(selected.getTextCoordinate()), String.valueOf(selected));
        // the section runs from line 1 to the end: the band shows it
        Section section = text(file).sections().get(0);
        assertEquals(Optional.of(new SectionBand.Lines(0, section.getTextCoordinate().endLine() - 1)), editor().sectionBand());
    }

    @Test
    void a_reading_not_kept_has_a_warning_marker_a_row_and_an_outline(FxRobot robot) throws IOException {
        grammars(robot, new NotebookGrammarPlugin(), new TagGrammarPlugin(), new DuplicateIgtGrammar());
        Path file = open(robot, "twice.md", NOTES);
        InterlinearSentence igt = igt(file);
        assertTrue(text(file).objects(DuplicateIgtGrammar.ID).isEmpty());
        Diagnostic warning = text(file).diagnostics(DuplicateIgtGrammar.ID).get(0);
        assertTrue(warning.isWarning());

        // ◆ in the margin; hovering it outlines the reading not kept
        waitUntil(() -> robot.lookup("#warningMarker").tryQuery().isPresent());
        Label marker = robot.lookup("#warningMarker").queryAs(Label.class);
        assertTrue(marker.getTooltip().getText().contains("Not kept"), marker.getTooltip().getText());
        robot.interact(() -> Event.fireEvent(marker, mouse(MouseEvent.MOUSE_ENTERED)));
        assertEquals(igt.getTextCoordinate().startIndex(), editor().outlinedRange().orElseThrow()[0]);
        robot.interact(() -> Event.fireEvent(marker, mouse(MouseEvent.MOUSE_EXITED)));
        assertTrue(editor().outlinedRange().isEmpty());

        // the errors tab lists the warning, with its severity, and counts it
        @SuppressWarnings("unchecked")
        TableView<Diagnostic> errors = robot.lookup("#errorTableView").queryAs(TableView.class);
        assertTrue(errors.getItems().stream().anyMatch(Diagnostic::isWarning));
        Tab errorsTab = robot.lookup(".tab-pane").queryAllAs(javafx.scene.control.TabPane.class).stream()
            .flatMap(p -> p.getTabs().stream()).filter(t -> t.getText().startsWith("Parsing errors")).findFirst().orElseThrow();
        assertEquals("Parsing errors (1 warning)", errorsTab.getText());
        // hiding the warnings is remembered
        javafx.scene.control.CheckBox show = robot.lookup("#showWarningsCheck").queryAs(javafx.scene.control.CheckBox.class);
        robot.interact(() -> show.setSelected(false));
        assertTrue(errors.getItems().isEmpty());
        assertFalse(settings.editorProperty().get().showWarnings());
        robot.interact(() -> show.setSelected(true));
        assertEquals(1, errors.getItems().size());
    }

    @Test
    void the_overlaps_tab_lists_the_project_and_shows_both(FxRobot robot) throws IOException {
        // empty state
        grammars(robot, new NotebookGrammarPlugin(), new DuplicateIgtGrammar());
        open(robot, "plain.md", "just prose\n");
        robot.interact(() -> fileMode().showOverlaps());
        @SuppressWarnings("unchecked")
        TableView<OverlapRows.Row> table = robot.lookup("#overlapsTable").queryAs(TableView.class);
        assertTrue(table.getItems().isEmpty());
        assertEquals("Overlaps", tabTitle(robot));
        assertTrue(((Label) table.getPlaceholder()).getText().startsWith("No overlapping readings"));

        // a file with a second reading: listed while the tab is shown
        Path file = addFile(robot, "twice.md", NOTES);
        waitUntil(() -> table.getItems().size() == 1);
        assertEquals("Overlaps (1)", tabTitle(robot));
        OverlapRows.Row row = table.getItems().get(0);
        assertEquals("Not kept", row.outcome());
        assertTrue(row.reading().startsWith("igt — Duplicate IGT reader"), row.reading());

        // Enter: the file is shown, the reading kept selected, the other outlined
        robot.interact(() -> {
            table.requestFocus();
            table.getSelectionModel().select(0);
        });
        robot.type(KeyCode.ENTER);
        waitUntil(() -> editor().getSelectedDocument().map(d -> d.getFilePath().equals(file)).orElse(false));
        InterlinearSentence igt = igt(file);
        assertSame(igt, editor().selectedObjectProperty().get());
        assertEquals(igt.getTextCoordinate().startIndex(), editor().outlinedRange().orElseThrow()[0]);
    }

    private static String tabTitle(FxRobot robot) {
        return robot.lookup(".tab-pane").queryAllAs(javafx.scene.control.TabPane.class).stream()
            .flatMap(p -> p.getTabs().stream()).filter(t -> "overlapsTab".equals(t.getId()))
            .findFirst().orElseThrow().getText();
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 0, 0, 0, 0, MouseButton.NONE, 0,
            false, false, false, false, false, false, false, false, false, false, null);
    }
}
