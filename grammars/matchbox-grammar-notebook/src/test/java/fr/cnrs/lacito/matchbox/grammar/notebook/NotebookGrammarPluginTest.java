package fr.cnrs.lacito.matchbox.grammar.notebook;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleExample;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotebookGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("sample.txt"));

    private String text;
    private ParseResult result;

    @BeforeEach
    void parse() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/sample.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        result = new NotebookGrammarPlugin().getResult(FILE, text);
    }

    private <T> List<T> objectsOf(Class<T> type) {
        return result.objects().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void parsesWithoutErrors() {
        assertTrue(result.diagnostics().isEmpty(), () -> "errors: " + result.diagnostics());
        assertEquals(3, result.objects().size());
        assertSpansNested(result.objects(), text);
        assertKindsPrefixed(result.objects(), NotebookGrammarPlugin.ID);
        for (CorpusObject o : result.objects()) {
            assertEquals(FILE, o.getTextCoordinate().text());
        }
    }

    @Test
    void extractsIgt() {
        List<SimpleIGT> igts = objectsOf(SimpleIGT.class);
        assertEquals(1, igts.size());
        SimpleIGT igt = igts.get(0);
        assertEquals("a mio-jaflo-m-wi-j", igt.getTranscription());
        assertEquals("1sg down-canoe-top-m.sg-pred", igt.getMorphemes());
        assertEquals("'I went down in canoe'", igt.getFreeTranslation());
        assertEquals(3, igt.getTextCoordinate().startLine());
        assertEquals(7, igt.getTextCoordinate().endLine());
        assertTrue(text.substring(igt.getTextCoordinate().startIndex()).startsWith(":::"));
    }

    @Test
    void extractsLexicalEntriesWithSensesAndExamples() {
        List<SimpleLexicalDefinition> entries = objectsOf(SimpleLexicalDefinition.class);
        assertEquals(2, entries.size());

        SimpleLexicalDefinition mami = entries.get(0);
        assertEquals("mami", mami.getHeadword());
        assertEquals(1, mami.getTextCoordinate().startLine());
        assertEquals(2, mami.getSenses().size());

        SimpleSense pig = mami.getSenses().get(0);
        assertEquals("pig", pig.getDefinition().strip());
        assertCovers("{pig «mami ka / the pig»}", pig.getTextCoordinate(), text);
        assertEquals(1, pig.getExamples().size());
        SimpleExample example = pig.getExamples().get(0);
        assertEquals("mami ka", example.getTranscription().strip());
        assertEquals("the pig", example.getTranslation().strip());
        assertCovers("«mami ka / the pig»", example.getTextCoordinate(), text);

        SimpleSense pork = mami.getSenses().get(1);
        assertEquals("pork", pork.getDefinition());
        assertTrue(pork.getExamples().isEmpty());

        SimpleLexicalDefinition jaflo = entries.get(1);
        assertEquals("jaflo", jaflo.getHeadword());
        assertEquals("canoe", jaflo.getSenses().get(0).getDefinition());
    }

    @Test
    void kindsAndLabels() {
        SimpleLexicalDefinition mami = objectsOf(SimpleLexicalDefinition.class).get(0);
        assertEquals(NotebookGrammarPlugin.KIND_ENTRY, mami.getKind());
        assertEquals("mami", mami.getLabel());
        assertEquals(mami.getSenses(), mami.getChildren());
        assertEquals(NotebookGrammarPlugin.KIND_SENSE, mami.getSenses().get(0).getKind());
        assertEquals(NotebookGrammarPlugin.KIND_IGT, objectsOf(SimpleIGT.class).get(0).getKind());
    }

    @Test
    void incompleteIgtIsSkippedOtherObjectsKept() {
        String partial = "<jaflo> {canoe}\n\n:::\na mio\n:::\n";
        ParseResult r = new NotebookGrammarPlugin().getResult(FILE, partial);
        assertTrue(!r.diagnostics().isEmpty());
        assertEquals(1, objectsOf(r, SimpleLexicalDefinition.class).size());
        assertTrue(objectsOf(r, SimpleIGT.class).isEmpty());
    }

    @Test
    void errorsAreLocatedByOffsetsWithExpectedTokens() {
        String partial = "<jaflo> {canoe}\n\n:::\na mio\n:::\n";
        ParseResult r = new NotebookGrammarPlugin().getResult(FILE, partial);
        assertTrue(!r.diagnostics().isEmpty());
        for (Diagnostic e : r.diagnostics()) {
            assertTrue(e.startIndex() >= 0 && e.startIndex() <= partial.length(), () -> "start: " + e);
            assertTrue(e.stopIndex() >= e.startIndex() - 1 && e.stopIndex() < partial.length(), () -> "stop: " + e);
            e.span().ifPresent(span -> assertTrue(span.isValidFor(partial.length()), () -> "span: " + span));
        }
        assertTrue(r.diagnostics().stream().anyMatch(e -> !e.expected().isEmpty()),
            () -> "no expected tokens: " + r.diagnostics());
    }

    private static <T> List<T> objectsOf(ParseResult r, Class<T> type) {
        return r.objects().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void successiveResultsAreIndependent() {
        NotebookGrammarPlugin plugin = new NotebookGrammarPlugin();
        ParseResult first = plugin.getResult(FILE, text);
        ParseResult second = plugin.getResult(FILE, "<jaflo> {canoe}\n");
        assertNotSame(first.objects(), second.objects());
        assertEquals(3, first.objects().size());
        assertEquals(1, second.objects().size());
    }

    @Test
    void linesOfOtherNotationsAreIgnored() {
        String withOthers = """
            ---
            speaker: Elaisa
            ---

            # Session 1

            Some free text: with punctuation, "quotes" and - dashes.
            ---
              indented text
            <mami> {pig}
            ## Nouns
            :::
            a mio-jaflo-m-wi-j
            1sg down-canoe-top-m.sg-pred
            'I went down in canoe'
            :::
            """;
        ParseResult r = new NotebookGrammarPlugin().getResult(FILE, withOthers);
        assertTrue(r.diagnostics().isEmpty(), () -> "errors: " + r.diagnostics());
        assertEquals(1, objectsOf(r, SimpleLexicalDefinition.class).size());
        assertEquals(1, objectsOf(r, SimpleIGT.class).size());
        assertSpansNested(r.objects(), withOthers);
    }
}
