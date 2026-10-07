package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectEditTest {

    @TempDir
    Path dir;
    private final ProjectParseTest.WholeTextGrammar notebook = new ProjectParseTest.WholeTextGrammar();
    private final GrammarPlugin other = new ProjectParseTest.WholeTextGrammar() {
        public String getId() { return "other-grammar"; }
    };
    private Project project;
    private Path a;
    private Path b;

    @BeforeEach
    void setUp() throws Exception {
        a = Files.writeString(dir.resolve("a.md"), "aaa");
        b = Files.writeString(dir.resolve("b.txt"), "bbb");
        project = Project.createDefaultProject(List.of(notebook, other));
        project.setName("Tuwari");
        project.setDescription("Field notes");
        FileType md = new FileType("all", ".*\\.md");
        md.addGrammar(other);
        project.addFileType(md);
        project.addFile(a);
    }

    @Test
    void theCopyHasTheDefinitionOfTheProject() {
        ProjectEdit edit = ProjectEdit.of(project);
        assertEquals("Tuwari", edit.getName());
        assertEquals("Field notes", edit.getDescription());
        assertEquals(List.of(a), edit.getPaths());
        assertEquals(List.of("All files", "all"), edit.getFileTypes().stream().map(ProjectEdit.FileTypeDraft::getName).toList());
        assertTrue(edit.getFileTypes().get(1).uses(other));
    }

    @Test
    void nothingChangesUntilApplied() {
        ProjectEdit edit = ProjectEdit.of(project);
        edit.setName("Other");
        edit.getFileTypes().get(1).setName("renamed");
        edit.getFileTypes().get(1).setUsed(other, false);
        edit.getFileTypes().remove(0);
        edit.getPaths().clear();
        assertEquals("Tuwari", project.getName());
        assertEquals(2, project.getFileTypes().size());
        assertEquals("all", project.getFileTypes().get(1).getName());
        assertEquals(List.of(other), project.getFileTypes().get(1).getGrammars());
        assertEquals(List.of(a), project.getDefinitionPaths());
    }

    @Test
    void applyingChangesGrammarsAndParsesAgain() throws Exception {
        assertEquals(List.of(Project.DEFAULT_PLUGIN_ID, "other-grammar"), ids(project.getGrammarsForFile(a)));
        ProjectEdit edit = ProjectEdit.of(project);
        ProjectEdit.FileTypeDraft all = edit.getFileTypes().get(1);
        all.setUsed(other, false);
        all.setUsed(notebook, true);
        edit.getFileTypes().add(new ProjectEdit.FileTypeDraft("texts", ".*\\.txt", List.of(other)));
        edit.getPaths().add(b);
        edit.setName("Renamed");

        ProjectEdit.Result result = edit.applyTo(project);
        assertTrue(result.problems().isEmpty(), result.problems()::toString);
        assertEquals(1, result.reparsed(), "a.md lost the other grammar");
        assertEquals("Renamed", project.getName());
        assertEquals(List.of(Project.DEFAULT_PLUGIN_ID), ids(project.getGrammarsForFile(a)));
        assertEquals(List.of("notebook-grammar"), project.getCorpus().text(TextId.of(a)).orElseThrow().grammarIds());
        assertEquals(List.of(a, b), project.getDefinitionPaths());
        assertEquals(List.of(Project.DEFAULT_PLUGIN_ID, "other-grammar"), ids(project.getGrammarsForFile(b)));
        assertTrue(project.getLoadProblems().isEmpty(), "no file added twice");
    }

    @Test
    void removingAPathDropsItsDocuments() {
        ProjectEdit edit = ProjectEdit.of(project);
        edit.getPaths().remove(a);
        assertEquals(1, edit.droppedDocuments(project).size());
        edit.applyTo(project);
        assertTrue(project.getDerivedFileList().isEmpty());
        assertTrue(project.getCorpus().text(TextId.of(a)).isEmpty());
    }

    @Test
    void directoriesAreAddedAndRemoved() throws Exception {
        Path sub = Files.createDirectories(dir.resolve("sub"));
        Files.writeString(sub.resolve("c.md"), "ccc");
        ProjectEdit edit = ProjectEdit.of(project);
        edit.getPaths().add(sub);
        edit.applyTo(project);
        assertEquals(2, project.getDerivedFileList().size());
        ProjectEdit again = ProjectEdit.of(project);
        again.getPaths().remove(sub);
        again.applyTo(project);
        assertEquals(1, project.getDerivedFileList().size());
    }

    @Test
    void invalidDefinitionsAreRefused() {
        ProjectEdit edit = ProjectEdit.of(project);
        edit.getFileTypes().get(1).setRegex("*.md(");
        edit.getFileTypes().get(0).setName(" ");
        assertEquals(2, edit.problems().size());
        assertThrows(IllegalStateException.class, () -> edit.applyTo(project));
        assertEquals("all", project.getFileTypes().get(1).getName());
    }

    @Test
    void grammarsCanBeReordered() {
        ProjectEdit.FileTypeDraft draft = new ProjectEdit.FileTypeDraft("t", ".+", List.of(notebook, other));
        draft.move(other, -1);
        assertEquals(List.of("other-grammar", Project.DEFAULT_PLUGIN_ID), ids(draft.getGrammars()));
        draft.move(other, -1);
        assertEquals(List.of("other-grammar", Project.DEFAULT_PLUGIN_ID), ids(draft.getGrammars()));
        assertFalse(new ProjectEdit.FileTypeDraft("t", ".+", List.of()).uses(other));
    }

    private static List<String> ids(List<GrammarPlugin> grammars) {
        return grammars.stream().map(GrammarPlugin::getId).toList();
    }
}
