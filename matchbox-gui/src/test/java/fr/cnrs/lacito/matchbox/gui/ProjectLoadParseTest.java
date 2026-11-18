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

    @Test
    void aProjectReadingIgtsWithTheNotebookGrammarGetsTheIgtGrammar() throws Exception {
        Path notes = Files.writeString(dir.resolve("old.txt"), "<mami> {pig}\n\n:::\na mio\n1sg go\n'I go'\n:::\n");
        Path mbx = Files.writeString(dir.resolve("old.mbx"), """
            <project name="Old">
              <fileset><file path="%s" /></fileset>
              <pattern><name>Notes</name><regex>.*\\.txt</regex><grammar id="notebook-grammar" rank="0" /></pattern>
            </project>
            """.formatted(notes.toString().replace("&", "&amp;")));
        Project loaded = Project.loadProject(PluginManager.loadPlugins(), mbx.toFile());
        assertEquals(java.util.List.of("notebook-grammar", "igt-grammar"),
            loaded.getFileTypes().get(0).getGrammars().stream().map(g -> g.getId()).toList());
        assertEquals(1, loaded.getLoadNotes().size());
        assertTrue(loaded.definitionChangedProperty().get(), "saving keeps the migration");
        var text = loaded.getCorpus().text(loaded.getParsedText(notes).getId()).orElseThrow();
        assertEquals(1, text.objectsOf(InterlinearSentence.class).size());
        assertEquals(1, text.objectsOf(LexicalEntry.class).size());
    }

    /**
     * The projects of the test data use paths relative to their directory, so that they open on
     * every machine (not all their plugins are on this module's test class path: the paths are
     * checked, not the projects loaded).
     */
    @Test
    void theTestProjectsUsePathsRelativeToTheirDirectory() throws Exception {
        java.util.regex.Pattern attribute = java.util.regex.Pattern.compile("path=\"([^\"]*)\"");
        for (String name : java.util.List.of("liftpatchbox.mbx", "projects/tuwari.mbx", "projects/project1.mbx")) {
            Path mbx = Path.of("src", "test", "data").resolve(name);
            var m = attribute.matcher(Files.readString(mbx));
            int count = 0;
            while (m.find()) {
                count++;
                assertFalse(Path.of(m.group(1)).isAbsolute(), name + ": " + m.group(1));
                Path resolved = fr.cnrs.lacito.matchbox.corpus.ProjectPaths.resolve(m.group(1),
                    java.util.Optional.of(mbx.toAbsolutePath().getParent()));
                assertTrue(Files.exists(resolved), name + ": " + resolved);
            }
            assertTrue(count > 0, name);
        }
    }
}
