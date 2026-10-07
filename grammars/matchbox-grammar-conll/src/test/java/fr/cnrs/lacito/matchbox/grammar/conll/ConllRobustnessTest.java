package fr.cnrs.lacito.matchbox.grammar.conll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConllRobustnessTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));

    private static List<DependencySentence> sentences(ParseResult r) {
        return r.objects().stream().map(DependencySentence.class::cast).toList();
    }

    @Test
    void otherLinesAreIgnored() {
        String text = """
            # Session 1

            Some notes about 2024 and 3 speakers.
            12 items without tab
            # sent_id = s1
            # text = The dog
            1	The	the	DET	_	_	2	det	_	_
            2	dog	dog	NOUN	_	_	0	root	_	_
            Free text right after the sentence.

            # a stray comment
            1	stray	token	X	_	_	0	root	_	_
            """;
        ParseResult r = new ConllGrammarPlugin().getResult(FILE, text);
        assertTrue(r.diagnostics().isEmpty(), () -> "errors: " + r.diagnostics());
        assertEquals(1, r.objects().size());
        DependencySentence s = sentences(r).get(0);
        assertEquals("s1", s.getSentenceId());
        assertEquals("The dog", s.getMetadata().get("text"));
        assertEquals(2, s.getTokens().size());
    }

    @Test
    void commentsBeforeSentIdValuesWithEqualsMultiwordAndEmptyNodes() {
        String text = "# newdoc id = d1\n# sent_id = s2\n# text = a = b\n# just a comment\n"
            + "1-2\tdu\t_\t_\t_\t_\t_\t_\t_\t_\n1\tde\tde\tADP\t_\t_\t2\tcase\t_\t_\n"
            + "2\tle\tle\tDET\t_\t_\t0\troot\t_\t_\n2.1\tx\tx\tX\t_\t_\t_\t_\t_\t_";
        ParseResult r = new ConllGrammarPlugin().getResult(FILE, text);
        assertTrue(r.diagnostics().isEmpty(), () -> "errors: " + r.diagnostics());
        DependencySentence s = sentences(r).get(0);
        assertEquals("s2", s.getSentenceId());
        assertEquals("d1", s.getMetadata().get("newdoc id"));
        assertEquals("a = b", s.getMetadata().get("text"));
        assertEquals(List.of("1-2", "1", "2", "2.1"), s.getTokens().stream().map(t -> t.get("ID")).toList());
        assertEquals("du", s.getTokens().get(0).get("FORM"), "the ID of a multiword token is not a field");
        assertEquals("x", s.getTokens().get(3).get("FORM"), "last line without final newline is kept");
    }

    @Test
    void aSentenceIsABlockOfConsecutiveLines() {
        String text = "# sent_id = a\n1\tx\tx\tX\t_\t_\t0\troot\t_\t_\n\n1\tlost\tx\tX\t_\t_\t0\troot\t_\t_\n# sent_id = b\n";
        ParseResult r = new ConllGrammarPlugin().getResult(FILE, text);
        assertTrue(r.diagnostics().isEmpty(), () -> "errors: " + r.diagnostics());
        assertEquals(List.of("a", "b"), sentences(r).stream().map(DependencySentence::getSentenceId).toList());
        assertEquals(1, sentences(r).get(0).getTokens().size());
        assertEquals(0, sentences(r).get(1).getTokens().size());
    }

    @Test
    void examplesAreValidConllU() {
        ConllGrammarPlugin plugin = new ConllGrammarPlugin();
        for (var example : plugin.getExamples()) {
            ParseResult r = plugin.getResult(FILE, example.text());
            assertTrue(r.diagnostics().isEmpty(), () -> example.caption() + ": " + r.diagnostics());
            assertEquals(1, r.objects().size(), example.caption());
            DependencySentence s = sentences(r).get(0);
            assertEquals(6, s.getTokens().size(), example.caption());
            for (var token : s.getTokens()) {
                assertEquals(10, token.size(), () -> "all ten fields: " + token);
            }
        }
    }
}

