package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectParseTest {

    /** Produces one object covering the whole text; can be made to wait on a latch. */
    static class WholeTextGrammar implements GrammarPlugin {
        volatile CountDownLatch gate;

        public String getId() { return Project.DEFAULT_PLUGIN_ID; }
        public String getDisplayName() { return "test"; }
        public String getDisplayDescription() { return "test"; }
        public int getApiVersion() { return API_VERSION; }

        public ParseResult getResult(Path file, String content) {
            if (gate != null) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new ParseResult(List.of(new SimpleIGT(getId() + ":igt",
                new TextCoordinate(file, 0, content.length() - 1, 1, 1), content, "", "")), List.of());
        }
    }

    static class FailingGrammar extends WholeTextGrammar {
        public String getId() { return "failing"; }
        public ParseResult getResult(Path file, String content) { throw new IllegalStateException("boom"); }
    }

    @Test
    void synchronousParse(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(file);
        assertEquals("hello", ((SimpleIGT) project.getCorpus().objectsFor(file).get(0)).getTranscription());
    }

    @Test
    void failingGrammarIsReportedAsError(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.getFileTypes().get(0).addGrammar(new FailingGrammar());
        project.addFile(file);
        assertEquals(1, project.getCorpus().objectsFor(file).size());
        assertEquals(1, project.getCorpus().errorsFor(file).size());
        assertEquals("failing", project.getCorpus().errorsFor(file).get(0).getGrammarId());
    }

    @Test
    void backgroundParseAppliesResult(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(file);
        project.updateFileContent("hello world", file);
        assertTrue(project.getParsedText(file).getCouldBeReparsed());

        project.parseInBackground(file).get(5, TimeUnit.SECONDS);
        assertEquals("hello world", ((SimpleIGT) project.getCorpus().objectsFor(file).get(0)).getTranscription());
        assertFalse(project.getParsedText(file).getCouldBeReparsed());
    }

    @Test
    void outdatedBackgroundResultIsDiscarded(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        WholeTextGrammar grammar = new WholeTextGrammar();
        Project project = Project.createDefaultProject(List.of(grammar));
        project.addFile(file);

        grammar.gate = new CountDownLatch(1);
        project.updateFileContent("version 1", file);
        var future = project.parseInBackground(file);
        project.updateFileContent("version 2", file); // edited while parsing
        grammar.gate.countDown();
        future.get(5, TimeUnit.SECONDS);

        assertEquals("hello", ((SimpleIGT) project.getCorpus().objectsFor(file).get(0)).getTranscription());
        assertTrue(project.getParsedText(file).getCouldBeReparsed());
    }
}
