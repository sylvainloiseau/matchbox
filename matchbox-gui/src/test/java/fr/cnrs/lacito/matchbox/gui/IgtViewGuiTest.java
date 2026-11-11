package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.grammar.igt.IgtGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarfx.igt.InterlinearGrid;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The built-in view of interlinear glossed texts: grid, cell editing, words, metadata. */
class IgtViewGuiTest extends MainWindowFixture {

    static final String NOTES = """
        :::{id="1.1", olang="fra", mlang="en"}
        je crois pas qu'elle est assez curieuse
        ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z
        1SG=believe not COMPL=3SG.F be\\3SG.PRES enough curious-FEM
        'I don't think she is curious enough'
        :::

        :::
        a mio-jaflo
        1sg down-canoe
        :::
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

    private InterlinearGlossedText igt(Path file, int index) {
        return text(file).objectsOf(InterlinearGlossedText.class).get(index);
    }

    private Path open(FxRobot robot) throws IOException {
        grammars(robot, new IgtGrammarPlugin());
        Path file = addFile(robot, "igt.md", NOTES);
        waitUntil(() -> workbench().session().corpus().text(document(file).getId()).isPresent());
        showFile(robot, file);
        return file;
    }

    private InterlinearGrid select(FxRobot robot, Path file, int index) {
        robot.interact(() -> fileMode().getEditorController().selectedObjectProperty().set(igt(file, index)));
        waitUntil(() -> robot.lookup("#igtGridView").tryQuery().isPresent());
        return robot.lookup("#igtGridView").queryAs(InterlinearGrid.class);
    }

    @Test
    void the_grid_shows_and_edits_the_words(FxRobot robot) throws IOException {
        Path file = open(robot);
        InterlinearGrid grid = select(robot, file, 0);
        // two segmented lines (transcription, gloss) of 6 words; the orthographic line is apart
        assertEquals(12, grid.cells().size());
        assertTrue(robot.lookup("#igtOrthographic").tryQuery().isPresent());
        assertTrue(robot.lookup("#igtTranslation").tryQuery().isPresent());

        // a word that fits: written in the text, one undo entry
        TextField not = grid.cell(2, 1).orElseThrow();
        robot.interact(() -> {
            not.setText("NEG");
            not.fireEvent(new javafx.event.ActionEvent());
        });
        waitUntil(() -> document(file).getCurrentText().contains("1SG=believe NEG COMPL"));
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(file).getCurrentText().equals(NOTES));
    }

    @Test
    void a_word_that_does_not_fit_asks_before_writing(FxRobot robot) throws IOException {
        Path file = open(robot);
        InterlinearGrid grid = select(robot, file, 0);
        TextField curious = grid.cell(2, 5).orElseThrow();
        robot.interact(() -> {
            curious.setText("curious");
            curious.fireEvent(new javafx.event.ActionEvent());
        });
        assertTrue(grid.messageBar().isVisible());
        assertTrue(grid.messageBar().getChildren().stream().anyMatch(n -> n instanceof javafx.scene.control.Button b
            && b.getText().equals("Write anyway")));
        assertEquals(NOTES, document(file).getCurrentText(), "nothing written yet");
        javafx.scene.control.Button revert = grid.messageBar().getChildren().stream()
            .filter(n -> n instanceof javafx.scene.control.Button b && b.getText().equals("Revert"))
            .map(javafx.scene.control.Button.class::cast).findFirst().orElseThrow();
        robot.interact(revert::fire);
        assertEquals("curious-FEM", curious.getText());
        assertFalse(grid.messageBar().isVisible());
    }

    @Test
    void insert_word_and_format_are_one_change_each(FxRobot robot) throws IOException {
        Path file = open(robot);
        InterlinearGrid grid = select(robot, file, 0);
        robot.interact(() -> grid.insertWord(2));
        waitUntil(() -> document(file).getCurrentText().contains("ʒə=kʁwa pa ? k=ɛl"));
        String text = document(file).getCurrentText();
        assertTrue(text.contains("1SG=believe not ? COMPL"), text);
        assertTrue(text.contains("je crois pas qu'elle est assez curieuse"), "the orthographic line is not touched");
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(file).getCurrentText().equals(NOTES));

        InterlinearGrid again = select(robot, file, 0);
        robot.interact(again::format);
        waitUntil(() -> document(file).getCurrentText().contains("ʒə=kʁwa     pa"));
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(file).getCurrentText().equals(NOTES));
    }

    @Test
    void a_metadata_entry_is_added_with_its_block(FxRobot robot) throws IOException {
        Path file = open(robot);
        select(robot, file, 1);
        robot.interact(() -> workbench().viewContext().addField(igt(file, 1),
            InterlinearGlossedText.metadataField("id"), "2"));
        waitUntil(() -> document(file).getCurrentText().contains(":::{id=\"2\"}\na mio-jaflo"));
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(file).getCurrentText().equals(NOTES));
    }
}
