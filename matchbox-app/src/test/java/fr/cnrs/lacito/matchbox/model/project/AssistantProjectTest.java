package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    void corpusKeepsTheParsedText() {
        var corpus = emptyProject().getCorpus();
        Path f = dir.resolve("a.txt");
        corpus.setResults(f, Map.of("g", ParseResult.empty()), "text");
        assertEquals("text", corpus.parsedText(f).orElseThrow());
        assertEquals(List.of(f), corpus.files());
        corpus.removeFile(f);
        assertTrue(corpus.parsedText(f).isEmpty());
    }
}
