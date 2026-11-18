package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefGrammarPlugin;
import fr.cnrs.lacito.matchbox.model.check.MediaCheck;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** Media references in the window: where a file was looked for, and the Media check. */
class MediaPathsGuiTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private Path project;
    private Path file;

    /** project/p.mbx, project/notes/day1.txt with "@media rec/a.wav 0:01-0:02 Greeting". */
    private void setUp(FxRobot robot) throws IOException {
        project = Files.createDirectories(dir.resolve("project"));
        Path notes = Files.createDirectories(project.resolve("notes"));
        file = Files.writeString(notes.resolve("day1.txt"), "@media rec/a.wav 0:01-0:02 Greeting\n");
        robot.interact(() -> {
            project().setFile(project.resolve("p.mbx").toFile());
            FileType type = new FileType("All files", ".*\\..*");
            type.addGrammar(new MediaRefGrammarPlugin());
            project().setFileTypes(List.of(type));
            try {
                project().addFile(file);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        waitUntil(() -> text().isPresent());
    }

    private java.util.Optional<Text> text() {
        return workbench().session().corpus().text(document(file).getId());
    }

    private List<Diagnostic> mediaIssues() {
        return workbench().problems().diagnosticsOf(text().orElseThrow()).stream()
            .filter(d -> MediaCheck.ID.equals(d.grammarId())).toList();
    }

    @Test
    void aMissingFileIsReportedWithBothPlacesAndDisappearsOnceCreated(FxRobot robot) throws IOException {
        setUp(robot);
        String sep = File.separator;
        String looked = "looked in notes" + sep + "rec" + sep + "a.wav, then rec" + sep + "a.wav";
        waitUntil(() -> mediaIssues().size() == 1);
        assertEquals("Media file not found: rec/a.wav (" + looked + ")", mediaIssues().get(0).message());

        // the media segment view says where it looked
        MediaSegment segment = text().orElseThrow().objectsOf(MediaSegment.class).get(0);
        robot.interact(() -> workbench().navigator().reveal(segment));
        waitUntil(() -> robot.lookup((Label l) -> l.getText() != null && l.getText().startsWith("File not found"))
            .tryQuery().isPresent());
        Label hint = robot.lookup((Label l) -> l.getText() != null && l.getText().startsWith("File not found")).query();
        assertEquals("File not found: rec/a.wav — " + looked, hint.getText());

        // the file is put in the project directory; the next parse finds it
        Files.createDirectories(project.resolve("rec"));
        Files.createFile(project.resolve("rec/a.wav"));
        robot.interact(() -> project().parsePath(file));
        waitUntil(() -> mediaIssues().isEmpty());
    }

    @Test
    void aNewProjectDirectoryChecksAgain(FxRobot robot) throws IOException {
        setUp(robot);
        waitUntil(() -> mediaIssues().size() == 1);
        // Save as… elsewhere: the reference is now looked for in the new project directory
        Path other = Files.createDirectories(dir.resolve("other"));
        Files.createDirectories(other.resolve("rec"));
        Files.createFile(other.resolve("rec/a.wav"));
        robot.interact(() -> project().setFile(other.resolve("p.mbx").toFile()));
        waitUntil(() -> mediaIssues().isEmpty());
    }
}
