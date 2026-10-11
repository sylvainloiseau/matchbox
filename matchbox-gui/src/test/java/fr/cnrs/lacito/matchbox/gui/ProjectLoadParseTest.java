package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A loaded project is parsed with the grammars of its file types: nothing has to parse it again
 * (the window reopening the last project at startup does not). Regression: the files used to be
 * added, and parsed, before the file types were read.
 */
class ProjectLoadParseTest {

    @TempDir
    Path dir;

    private static long objects(Project p, Path file) {
        return p.getCorpus().text(p.getParsedText(file).getId()).map(t -> t.flatten().count()).orElse(-1L);
    }

    @Test
    void aLoadedProjectIsAlreadyParsed() throws Exception {
        Path notes = Files.writeString(dir.resolve("notes.txt"), "<mami> {pig}\n\n:::\na mio\n1sg go\n'I go'\n:::\n");
        Path d = Files.createDirectories(dir.resolve("d"));
        Path inDirectory = Files.writeString(d.resolve("in.txt"), "<jaflo> {canoe}\n");
        Project saved = Project.createDefaultProject(PluginManager.loadPlugins());
        saved.addFile(notes);
        saved.addDirectory(d, true);
        saved.setFile(dir.resolve("p.mbx").toFile());
        ProjectXMLSerializer.save(saved);

        Project loaded = Project.loadProject(PluginManager.loadPlugins(), dir.resolve("p.mbx").toFile());
        var notesText = loaded.getCorpus().text(loaded.getParsedText(notes).getId()).orElseThrow();
        assertEquals(1, notesText.objectsOf(LexicalEntry.class).size());
        assertEquals(1, notesText.objectsOf(InterlinearSentence.class).size());
        assertTrue(objects(loaded, inDirectory) > 0, "files of a directory too");
        assertFalse(loaded.definitionChangedProperty().get());
    }
}
