package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingActions;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/**
 * Edits asked by views after the user typed without parsing: moved by the history of the document
 * (versioned edits), refused with a Show link when the user changed their place.
 */
class VersionedEditGuiTest extends MainWindowFixture {

    /** Two IGTs with the same translation, close to each other. */
    static final String NOTES = """
        :::
        a mio
        1sg down
        'I went'
        :::

        :::
        a jaflo
        1sg canoe
        'I went'
        :::
        """;

    private static final EditOrigin TYPING = EditOrigin.of(EditOrigin.Kind.TYPING, "keyboard");

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
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

    /** Typing: not parsed until asked. */
    private void type(FxRobot robot, Path file, int offset, String s) {
        robot.interact(() -> assertTrue(document(file).applyEdits(List.of(TextEdit.insert(offset, s)), TYPING)));
    }

    private EditOutcome setTranslation(FxRobot robot, InterlinearSentence igt, String value) {
        EditOutcome[] outcome = new EditOutcome[1];
        robot.interact(() -> outcome[0] = workbench().edits().setField(igt, InterlinearSentence.FREE_TRANSLATION, value));
        return outcome[0];
    }

    @Test
    void the_edited_igt_changes_not_a_nearer_copy(FxRobot robot) throws IOException {
        Path file = open(robot, "notes.txt", NOTES);
        InterlinearSentence second = text(file).objectsOf(InterlinearSentence.class).get(1);
        // typed above, more than the distance between the two IGTs; not parsed
        type(robot, file, 0, "% " + "a long remark ".repeat(10) + "\n");
        assertTrue(document(file).getCouldBeReparsed());
        assertTrue(setTranslation(robot, second, "We went").isApplied());
        String now = document(file).getCurrentText();
        assertTrue(now.indexOf("'I went'") < now.indexOf("a jaflo"), now);
        assertTrue(now.indexOf("'We went'") > now.indexOf("a jaflo"), now);
        // and the editor shows it
        waitUntil(() -> robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class).getText().equals(
            document(file).getCurrentText()));
    }

    @Test
    void a_text_action_inserting_after_typing_is_applied(FxRobot robot) throws IOException {
        Path file = open(robot, "plain.md", "Some prose.\n");
        List<TextAction> actions = new MarkdownHeadingActions().actionsFor(text(file), null);
        assertEquals(1, actions.size(), "Add header offered");
        type(robot, file, document(file).getCurrentText().length(), "More prose.\n");
        EditOutcome[] outcome = new EditOutcome[1];
        robot.interact(() -> outcome[0] = workbench().edits().runTextAction(document(file).getId(), actions.get(0)));
        assertTrue(outcome[0].isApplied(), outcome[0].message());
        assertTrue(document(file).getCurrentText().startsWith("---\n"), document(file).getCurrentText());
        assertTrue(document(file).getCurrentText().endsWith("Some prose.\nMore prose.\n"));
    }

    @Test
    void a_change_of_the_field_is_refused_with_a_show_link(FxRobot robot) throws IOException {
        Path file = open(robot, "notes.txt", NOTES);
        InterlinearSentence second = text(file).objectsOf(InterlinearSentence.class).get(1);
        int inTranslation = NOTES.lastIndexOf("went");
        type(robot, file, inTranslation, "x");
        EditOutcome outcome = setTranslation(robot, second, "We went");
        assertInstanceOf(EditOutcome.Stale.class, outcome);
        assertTrue(outcome.message().startsWith("You changed line 10"), outcome.message());
        assertEquals("Not applied: line 10 of notes.txt changed since it was parsed",
            workbench().status().messageProperty().get());
        assertEquals("Show", workbench().status().linkTextProperty().get());

        // Show: the File Mode editor at that line
        robot.interact(() -> {
            InlineCssTextArea area = robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class);
            area.moveTo(0);
            workbench().status().followLink();
        });
        FileMode fileMode = (FileMode) workbench().modes().orElseThrow().get(Modes.FILE);
        InlineCssTextArea area = robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class);
        waitUntil(() -> area.getCurrentParagraph() == 9);
        assertTrue(fileMode.getEditorController().getSelectedDocument().isPresent());
    }
}
