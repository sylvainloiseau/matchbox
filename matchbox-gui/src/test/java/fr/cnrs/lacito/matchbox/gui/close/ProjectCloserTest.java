package fr.cnrs.lacito.matchbox.gui.close;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCloserTest {

    @TempDir
    Path dir;

    private final List<UnsavedProjectState> asked = new ArrayList<>();
    private final List<Project> definitionsSaved = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private boolean definitionSaveSucceeds = true;

    private ProjectCloser closer(Optional<CloseChoice> answer) {
        return new ProjectCloser((state, action) -> {
            asked.add(state);
            return answer;
        }, p -> {
            definitionsSaved.add(p);
            return definitionSaveSucceeds;
        }, (title, e) -> errors.add(title));
    }

    private Project project() throws IOException {
        Project p = Project.createDefaultProject(PluginManager.loadPlugins());
        p.setApplyExecutor(Runnable::run);
        return p;
    }

    private TextDocument modifiedFile(Project p, String name) throws IOException {
        Path file = Files.writeString(dir.resolve(name), "hello");
        p.addFile(file);
        TextDocument d = p.getParsedText(file);
        assertTrue(d.applyEdits(List.of(TextEdit.replace("hello", 0, 5, "HELLO")), EditOrigin.of(EditOrigin.Kind.VIEW, "test")));
        return d;
    }

    @Test
    void nothingIsAskedWhenNothingWouldBeLost() throws IOException {
        assertTrue(closer(Optional.empty()).confirm(project(), CloseAction.CLOSE));
        assertTrue(closer(Optional.empty()).confirm(null, CloseAction.CLOSE));
        assertTrue(asked.isEmpty());
    }

    @Test
    void aChangedDefinitionAloneIsAYesNoQuestion() throws IOException {
        Project p = project();
        p.setName("fieldwork");
        assertTrue(closer(Optional.of(new CloseChoice(true, List.of()))).confirm(p, CloseAction.CLOSE));
        assertTrue(asked.get(0).definitionOnly());
        assertEquals(List.of(p), definitionsSaved);
    }

    @Test
    void onlyTheSelectedFilesAreSaved() throws IOException {
        Project p = project();
        TextDocument a = modifiedFile(p, "a.txt");
        TextDocument b = modifiedFile(p, "b.txt");
        assertTrue(closer(Optional.of(new CloseChoice(false, List.of(b)))).confirm(p, CloseAction.QUIT));
        UnsavedProjectState state = asked.get(0);
        assertTrue(state.definitionChanged(), "files were added");
        assertEquals(List.of(a, b), state.dirty());
        assertEquals("hello", Files.readString(a.getFilePath()));
        assertEquals("HELLO", Files.readString(b.getFilePath()));
        assertTrue(definitionsSaved.isEmpty());
    }

    @Test
    void cancelKeepsTheProjectOpenAndSavesNothing() throws IOException {
        Project p = project();
        TextDocument a = modifiedFile(p, "a.txt");
        assertFalse(closer(Optional.empty()).confirm(p, CloseAction.CLOSE));
        assertEquals("hello", Files.readString(a.getFilePath()));
    }

    @Test
    void aDefinitionThatIsNotSavedStopsTheClose() throws IOException {
        Project p = project();
        TextDocument a = modifiedFile(p, "a.txt");
        definitionSaveSucceeds = false;
        assertFalse(closer(Optional.of(new CloseChoice(true, List.of(a)))).confirm(p, CloseAction.CLOSE));
        assertEquals("hello", Files.readString(a.getFilePath()), "files are saved after the definition");
    }

    @Test
    void aFileThatCannotBeSavedStopsTheClose() throws IOException {
        Project p = project();
        TextDocument a = modifiedFile(p, "a.txt");
        Files.delete(a.getFilePath());
        Files.createDirectory(a.getFilePath());
        assertFalse(closer(Optional.of(new CloseChoice(false, List.of(a)))).confirm(p, CloseAction.CLOSE));
        assertEquals(1, errors.size());
    }

    @Test
    void pathsAreShownRelativeToTheProjectDirectory() {
        assertEquals(Path.of("notes", "a.txt").toString(),
            CloseProjectDialog.displayPath(dir.resolve("notes/a.txt"), Optional.of(dir)));
        Path elsewhere = dir.getParent().resolve("x.txt").toAbsolutePath();
        assertEquals(elsewhere.toString(), CloseProjectDialog.displayPath(elsewhere, Optional.of(dir)));
    }
}
