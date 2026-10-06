package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssistantProjectTest {

    @TempDir
    Path dir;

    private Project emptyProject() {
        Project p = Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
        // no file type: loading does not need the grammars
        List.copyOf(p.getFileTypes()).forEach(p::removeFileType);
        return p;
    }

    @Test
    void cloudRestrictionIsSavedInTheProjectFile() throws Exception {
        Project p = emptyProject();
        assertTrue(p.isCloudAssistAllowed());
        p.setCloudAssistAllowed(false);
        assertTrue(p.isProjectDefinitionOutdatedProperty().get());
        Path file = dir.resolve("p.mbx");
        p.setFile(file.toFile());
        ProjectXMLSerializer.save(p);
        assertTrue(Files.readString(file).contains("<assistant allowCloud=\"false\"/>"));
        assertFalse(ProjectXMLSerializer.load(file.toFile(), List.of()).isCloudAssistAllowed());

        p.setCloudAssistAllowed(true);
        ProjectXMLSerializer.save(p);
        assertFalse(Files.readString(file).contains("assistant"));
        assertTrue(ProjectXMLSerializer.load(file.toFile(), List.of()).isCloudAssistAllowed());
    }

    @Test
    void corpusKeepsTheParsedText() throws Exception {
        Project p = Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
        Path f = Files.writeString(dir.resolve("a.txt"), "text");
        p.addFile(f);
        TextId id = TextId.of(f);
        assertEquals("text", p.getCorpus().text(id).orElseThrow().content());
        assertEquals(List.of(id), p.getCorpus().texts().stream().map(Text::id).toList());
        p.removeFile(f);
        assertTrue(p.getCorpus().text(id).isEmpty());
    }
}
