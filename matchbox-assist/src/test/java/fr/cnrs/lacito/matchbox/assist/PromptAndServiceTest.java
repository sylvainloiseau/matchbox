package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.assist.prompt.FewShotExample;
import fr.cnrs.lacito.matchbox.assist.prompt.FewShotSelector;
import fr.cnrs.lacito.matchbox.assist.prompt.PromptPrefix;
import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest;
import fr.cnrs.lacito.matchbox.assist.provider.ChatResponse;
import fr.cnrs.lacito.matchbox.assist.provider.CompletionProvider;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderConfig;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderKind;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PromptAndServiceTest {

    private static final Path FILE = Path.of("notes.txt");
    private static final List<GrammarPlugin> GRAMMARS = List.of(new BracketGrammar());

    /** Answers with the given texts in order, and records the requests. */
    static final class FakeProvider implements CompletionProvider {
        final List<String> answers;
        final List<ChatRequest> requests = new ArrayList<>();

        FakeProvider(String... answers) {
            this.answers = new ArrayList<>(List.of(answers));
        }

        public ProviderConfig config() {
            return new ProviderConfig(ProviderKind.OLLAMA, "", "fake", "");
        }

        public CompletableFuture<ChatResponse> chat(ChatRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(new ChatResponse(answers.remove(0), 1, 1, 0, false));
        }

        public CompletableFuture<List<String>> listModels() {
            return CompletableFuture.completedFuture(List.of("fake"));
        }
    }

    private final EditValidator validator = new EditValidator();

    @AfterEach
    void close() {
        validator.close();
    }

    private static AssistContext context(String text, int caret) {
        List<ParseError> errors = new BracketGrammar().getResult(FILE, text).errors();
        return new AssistContext(FILE, text, caret, GRAMMARS, errors,
            PromptPrefix.build(GRAMMARS, List.of(new FewShotExample("lexical entry", "dog [animal]")), false));
    }

    private AssistService service(FakeProvider provider) {
        return new AssistService(provider, provider, validator, AssistOptions.defaults());
    }

    // ------------------------------------------------------------------
    // Prefix
    // ------------------------------------------------------------------

    @Test
    void prefixIsStableAndDocumentsTheGrammars() {
        List<FewShotExample> examples = List.of(new FewShotExample("lexical entry", "dog [animal]"));
        PromptPrefix a = PromptPrefix.build(GRAMMARS, examples, false);
        PromptPrefix b = PromptPrefix.build(GRAMMARS, new ArrayList<>(examples), false);
        assertEquals(a.text(), b.text());
        assertEquals(a.key(), b.key());
        assertTrue(a.blocks().get(a.blocks().size() - 1).cacheBreakpoint());
        String text = a.text();
        assertTrue(text.contains("## Brackets (grammar id: brackets)"));
        assertTrue(text.contains("Write [x] to mark a gloss."));
        assertTrue(text.contains("<example caption=\"a gloss\">"));
        assertTrue(text.contains("# Examples from this project"));
        assertFalse(text.contains("grammar Brackets;"), "source only on request");
        assertTrue(PromptPrefix.build(GRAMMARS, examples, true).text().contains("grammar Brackets;"));
        assertNotEquals(a.text(), PromptPrefix.build(List.of(), examples, false).text());

        PromptPrefix.Cache cache = new PromptPrefix.Cache();
        assertSame(cache.get(GRAMMARS, examples, false), cache.get(GRAMMARS, List.copyOf(examples), false));
    }

    @Test
    void emptyDocumentationIsOmitted() {
        GrammarPlugin undocumented = new BracketGrammar() {
            public String getFormatDocumentation() { return ""; }
            public List<GrammarExample> getExamples() { return List.of(); }
            public String getGrammarSource() { return ""; }
        };
        String text = PromptPrefix.build(List.of(undocumented), List.of(), true).text();
        assertFalse(text.contains("Canonical examples"));
        assertFalse(text.contains("<grammar-source>"));
    }

    @Test
    void fewShotSelectionSkipsObjectsWithErrors() {
        String text = "a\nb\nc\n";
        List<CorpusObject> objects = List.of(
            new SimpleIGT("g:igt", new TextCoordinate(FILE, 2, 2, 2, 2), "b", "", ""),
            new SimpleIGT("g:igt", new TextCoordinate(FILE, 0, 0, 1, 1), "a", "", ""),
            new SimpleIGT("g:igt", new TextCoordinate(FILE, 4, 4, 3, 3), "c", "", ""));
        List<ParseError> errors = List.of(new ParseError(FILE, "g", 2, 0, "bad", 2, 2, List.of()));
        List<FewShotExample> picked = FewShotSelector.select(List.of(new FewShotSelector.ParsedFile(FILE, text, objects, errors)));
        assertEquals(List.of("a", "c"), picked.stream().map(FewShotExample::text).toList());
    }

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    @Test
    void completionIsCleanedAndChecked() {
        FakeProvider provider = new FakeProvider("```\na [b]\n```");
        String text = "x\na [b";
        Completion c = service(provider).complete(context(text, text.length())).join().orElseThrow();
        assertEquals("]", c.text(), "fence and echo of the line removed");
        assertEquals(1, c.fixedErrors());
        String user = provider.requests.get(0).user();
        assertTrue(user.contains("a [b⟦CURSOR⟧"));
        assertTrue(user.contains("missing ']'"), "errors near the cursor are given");
    }

    @Test
    void completionThatBreaksTheGrammarIsDropped() {
        FakeProvider provider = new FakeProvider("] ]");
        String text = "a [b";
        assertTrue(service(provider).complete(context(text, text.length())).join().isEmpty());
    }

    @Test
    void cleaningLimitsLinesAndAvoidsDoubleLineBreaks() {
        assertEquals("1\n2\n", AssistService.cleanCompletion("1\n2\n3\n4", "", 0, 2));
        assertEquals("b", AssistService.cleanCompletion("b\n", "a\n", 1, 15));
        assertEquals("", AssistService.cleanCompletion("  \n", "", 0, 15));
    }

    // ------------------------------------------------------------------
    // Explanation and review
    // ------------------------------------------------------------------

    @Test
    void explanationOffersOnlyAFixThatParses() {
        String text = "a [b\n\nc";
        ParseError error = context(text, 0).errors().get(0);
        FakeProvider good = new FakeProvider("{\"explanation\":\"Close the bracket.\",\"original\":\"[b\",\"replacement\":\"[b]\"}");
        Explanation e = service(good).explain(context(text, 0), error).join();
        assertEquals("Close the bracket.", e.text());
        assertEquals("a [b]\n\nc", e.fix().orElseThrow().apply(text));
        assertTrue(good.requests.get(0).user().contains("in English"));
        assertTrue(good.requests.get(0).schema().isPresent());

        FakeProvider bad = new FakeProvider("Here: {\"explanation\":\"x\",\"original\":\"[b\",\"replacement\":\"[[b\"}");
        Explanation rejected = service(bad).explain(context(text, 0), error).join();
        assertTrue(rejected.fix().isEmpty());
        assertFalse(rejected.note().isEmpty());
    }

    @Test
    void explanationsAreCached() {
        String text = "a [b";
        FakeProvider provider = new FakeProvider("{\"explanation\":\"x\",\"original\":\"\",\"replacement\":\"\"}");
        AssistService service = service(provider);
        ParseError error = context(text, 0).errors().get(0);
        service.explain(context(text, 0), error).join();
        service.explain(context(text, 0), error).join();
        assertEquals(1, provider.requests.size());
    }

    @Test
    void reviewLocatesAndChecksEdits() {
        String text = "dog [animal\ncat [animal]\n";
        FakeProvider provider = new FakeProvider("""
            {"edits":[
              {"original":"dog [animal","replacement":"dog [animal]","explanation":"close","kind":"syntax"},
              {"original":"cat","replacement":"cat]","explanation":"bad","kind":"format"},
              {"original":"zebra","replacement":"x","explanation":"absent","kind":"format"}
            ]}""");
        ReviewResult r = service(provider).review(context(text, 0), 0, text.length()).join();
        assertEquals(1, r.accepted().size());
        assertEquals("dog [animal]", r.accepted().get(0).edit().replacement());
        assertEquals(1, r.accepted().get(0).fixedErrors());
        assertEquals(1, r.rejected().size());
        assertEquals(1, r.unlocated());
    }
}
