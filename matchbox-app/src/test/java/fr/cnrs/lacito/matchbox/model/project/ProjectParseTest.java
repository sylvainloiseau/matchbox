package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
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
        public String getFormatDocumentation() { return ""; }
        public List<GrammarExample> getExamples() { return List.of(); }
        public String getGrammarSource() { return ""; }
        public int getApiVersion() { return API_VERSION; }

        public ParseResult getResult(TextId file, String content) {
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
        public ParseResult getResult(TextId file, String content) { throw new IllegalStateException("boom"); }
    }

    private static final EditOrigin TYPING = EditOrigin.of(EditOrigin.Kind.TYPING, null);

    private static List<CorpusObject> objects(Project project, Path file) {
        return project.getCorpus().text(TextId.of(file)).orElseThrow().objects();
    }

    private static List<Diagnostic> diagnostics(Project project, Path file) {
        return project.getCorpus().text(TextId.of(file)).orElseThrow().diagnostics();
    }

    private static void type(Project project, Path file, String text) {
        project.getParsedText(file).replaceAll(text, TYPING);
    }

    @Test
    void synchronousParse(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(file);
        assertEquals("hello", ((SimpleIGT) objects(project, file).get(0)).getTranscription());
    }

    @Test
    void failingGrammarIsReportedAsError(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.getFileTypes().get(0).addGrammar(new FailingGrammar());
        project.addFile(file);
        assertEquals(1, objects(project, file).size());
        assertEquals(1, diagnostics(project, file).size());
        assertEquals("failing", diagnostics(project, file).get(0).grammarId());
    }

    @Test
    void backgroundParseAppliesResult(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(file);
        type(project, file, "hello world");
        assertTrue(project.getParsedText(file).getCouldBeReparsed());

        project.parseInBackground(file).get(5, TimeUnit.SECONDS);
        assertEquals("hello world", ((SimpleIGT) objects(project, file).get(0)).getTranscription());
        assertFalse(project.getParsedText(file).getCouldBeReparsed());
    }

    @Test
    void outdatedBackgroundResultIsDiscarded(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        WholeTextGrammar grammar = new WholeTextGrammar();
        Project project = Project.createDefaultProject(List.of(grammar));
        project.addFile(file);

        grammar.gate = new CountDownLatch(1);
        type(project, file, "version 1");
        var future = project.parseInBackground(file);
        type(project, file, "version 2"); // edited while parsing
        grammar.gate.countDown();
        future.get(5, TimeUnit.SECONDS);

        assertEquals("hello", ((SimpleIGT) objects(project, file).get(0)).getTranscription());
        assertTrue(project.getParsedText(file).getCouldBeReparsed());
    }

    @Test
    void theParsedTextCarriesTheVersionOfItsDocument(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(file);
        TextDocument document = project.document(TextId.of(file)).orElseThrow();
        assertEquals(document.getVersion(), project.getCorpus().text(TextId.of(file)).orElseThrow().version());
        type(project, file, "changed");
        project.parsePath(file);
        assertEquals(document.getVersion(), project.getCorpus().text(TextId.of(file)).orElseThrow().version());
    }

    @Test
    void nonTypingChangesAreParsedAtOnce(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(file);
        TextDocument document = project.document(TextId.of(file)).orElseThrow();
        java.util.concurrent.CompletableFuture<Void> applied = new java.util.concurrent.CompletableFuture<>();
        project.getCorpus().addListener((id, before, after) -> applied.complete(null));
        document.replaceAll("hello view", EditOrigin.of(EditOrigin.Kind.VIEW, this));
        applied.get(5, TimeUnit.SECONDS);
        assertEquals("hello view", ((SimpleIGT) objects(project, file).get(0)).getTranscription());
    }

    @Test
    void unsavedDocumentsAreTracked(@TempDir Path dir) throws Exception {
        Path a = Files.writeString(dir.resolve("a.txt"), "aaa");
        Path b = Files.writeString(dir.resolve("b.txt"), "bbb");
        Project project = Project.createDefaultProject(List.of(new WholeTextGrammar()));
        project.addFile(a);
        project.addFile(b);
        assertFalse(project.anyDocumentDirtyProperty().get());
        type(project, b, "bbbx");
        assertTrue(project.anyDocumentDirtyProperty().get());
        assertEquals(List.of(project.getParsedText(b)), project.getDirtyDocuments());
        project.saveAll();
        assertFalse(project.anyDocumentDirtyProperty().get());
        assertEquals("bbbx", Files.readString(b));
        type(project, a, "aaax");
        project.journal().undo();
        assertFalse(project.anyDocumentDirtyProperty().get(), "undo back to the saved text");
    }

    @Test
    void defaultProjectHasOneFileTypeWithEveryPluginSortedByName() {
        WholeTextGrammar tags = named("tag-grammar", "Tags");
        WholeTextGrammar conll = named("conll-grammar", "Conll Grammar");
        WholeTextGrammar notebook = named(Project.DEFAULT_PLUGIN_ID, "Notebook Grammar");
        WholeTextGrammar markdown = named("markdown-heading-grammar", "Markdown headings and metadata");
        Project project = Project.createDefaultProject(List.of(tags, conll, notebook, markdown));
        assertEquals(1, project.getFileTypes().size());
        FileType all = project.getFileTypes().get(0);
        assertEquals("All files", all.getName());
        assertEquals(".*\\..*", all.getRegex());
        assertEquals(List.of("conll-grammar", "markdown-heading-grammar", Project.DEFAULT_PLUGIN_ID, "tag-grammar"),
            project.getGrammarsForFile(Path.of("notes.md")).stream().map(g -> g.getId()).toList());
        assertEquals(4, project.getGrammarsForFile(Path.of("notes.txt")).size());
        assertTrue(project.getGrammarsForFile(Path.of("README")).isEmpty(), "no extension: not matched");
        assertFalse(project.definitionChangedProperty().get());
    }

    @Test
    void defaultProjectWithoutPluginHasAnEmptyFileType() {
        Project project = Project.createDefaultProject(List.of());
        assertEquals(1, project.getFileTypes().size());
        assertTrue(project.getFileTypes().get(0).getGrammars().isEmpty());
    }

    private static WholeTextGrammar named(String id, String name) {
        return new WholeTextGrammar() {
            public String getId() { return id; }
            public String getDisplayName() { return name; }
        };
    }
}

