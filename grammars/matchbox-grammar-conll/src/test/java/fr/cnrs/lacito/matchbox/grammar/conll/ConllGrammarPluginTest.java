package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.corpus.simple.SimpleDependencyTree;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Path;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConllGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("sample.conllu"));

    private String text;
    private ParseResult result;

    @BeforeEach
    void parse() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/sample.conllu")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        result = new ConllGrammarPlugin().getResult(FILE, text);
    }

    @Test
    void extractsOneObjectPerSentence() {
        assertTrue(result.diagnostics().isEmpty(), () -> "errors: " + result.diagnostics());
        List<CorpusObject> sentences = result.objects();
        assertEquals(2, sentences.size());
        assertSpansValid(sentences, text);
        assertEquals("s1", sentences.get(0).getLabel());
        assertEquals(ConllGrammarPlugin.KIND_SENTENCE, sentences.get(0).getKind());
        assertEquals(1, sentences.get(0).getTextCoordinate().startLine());
        assertEquals(6, sentences.get(1).getTextCoordinate().startLine());
        assertTrue(text.substring(sentences.get(1).getTextCoordinate().startIndex()).startsWith("# sent_id = s2"));
    }

    @Test
    void extractsTokensHeadsAndMetadata() {
        DependencySentence s1 = (DependencySentence) result.objects().get(0);
        assertEquals("s1", s1.getSentenceId());
        assertEquals(Map.of("text", "The dog"), s1.getMetadata());
        assertEquals(2, s1.getTokens().size());
        Map<String, String> dog = s1.getTokens().get(1);
        assertEquals("2", dog.get("ID"));
        assertEquals("dog", dog.get("FORM"));
        assertEquals("NOUN", dog.get("UPOS"));
        assertEquals("root", dog.get("DEPREL"));
        assertEquals(List.of("ID", "FORM", "LEMMA", "UPOS", "XPOS", "FEATS", "HEAD", "DEPREL", "DEPS", "MISC"),
            List.copyOf(dog.keySet()));
        assertEquals(List.of(2, 0), s1.getHeads());

        DependencySentence s2 = (DependencySentence) result.objects().get(1);
        assertEquals(Map.of(), s2.getMetadata());
        assertEquals(List.of(0), s2.getHeads());
    }

    @Test
    void successiveResultsAreIndependent() {
        ConllGrammarPlugin plugin = new ConllGrammarPlugin();
        ParseResult first = plugin.getResult(FILE, text);
        ParseResult second = plugin.getResult(FILE, "# sent_id = x\n1\ta\ta\tX\t_\t_\t0\troot\t_\t_\n");
        assertNotSame(first.objects(), second.objects());
        assertEquals(2, first.objects().size());
        assertEquals(1, second.objects().size());
    }

    @Test
    void sampleHasNoSemanticIssue() {
        Text t = Text.builder(FILE, text).add("conll", result.objects(), result.diagnostics()).build();
        assertTrue(t.diagnostics(Diagnostic.Category.SEMANTIC).isEmpty(), () -> t.diagnostics().toString());
    }

    @Test
    void wrongHeadIsReportedOnItsTokenLine() {
        String bad = "# sent_id = s1\n1\tA\ta\tX\t_\t_\t0\troot\t_\t_\n2\tB\tb\tX\t_\t_\t9\tdep\t_\t_\n\n";
        ParseResult r = new ConllGrammarPlugin().getResult(FILE, bad);
        Text t = Text.builder(FILE, bad).add("conll", r.objects(), r.diagnostics()).build();
        List<Diagnostic> semantic = t.diagnostics(Diagnostic.Category.SEMANTIC);
        assertEquals(1, semantic.size(), () -> t.diagnostics().toString());
        assertEquals(SimpleDependencyTree.CODE_HEAD_RANGE, semantic.get(0).code());
        assertEquals(3, semantic.get(0).line());
        assertEquals(bad.indexOf("2\tB"), semantic.get(0).startIndex());
    }
}
