package fr.cnrs.lacito.matchbox.grammarapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Claim;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class TextParserTest {

    private static final TextId ID = TextId.of(Path.of("t.txt"));
    private static final String CONTENT = "x".repeat(50);

    /** A grammar returning fixed objects; overrides only the old {@code getResult}. */
    private static class Fixed implements GrammarPlugin {
        private final String id;
        private final BiFunction<TextId, String, ParseResult> result;

        Fixed(String id, BiFunction<TextId, String, ParseResult> result) {
            this.id = id;
            this.result = result;
        }

        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return "Grammar " + id.toUpperCase(java.util.Locale.ROOT); }
        @Override public String getDisplayDescription() { return ""; }
        @Override public String getFormatDocumentation() { return ""; }
        @Override public List<GrammarExample> getExamples() { return List.of(); }
        @Override public String getGrammarSource() { return ""; }
        @Override public int getApiVersion() { return API_VERSION; }
        @Override public ParseResult getResult(TextId text, String content) { return result.apply(text, content); }
    }

    private static ParseResult igt(String grammar, int start, int stop) {
        return new ParseResult(List.of(new SimpleIGT(grammar + ":igt", new TextCoordinate(ID, start, stop, 1, 1),
            "t", "m", "f")), List.of());
    }

    @Test
    void each_grammar_receives_the_claims_of_the_earlier_ones() {
        List<ParseContext> received = new ArrayList<>();
        GrammarPlugin a = new Fixed("a", (t, c) -> igt("a", 10, 19));
        GrammarPlugin b = new Fixed("b", (t, c) -> igt("b", 30, 39)) {
            @Override
            public ParseResult getResult(TextId text, String content, ParseContext context) {
                received.add(context);
                return super.getResult(text, content, context);
            }
        };
        GrammarPlugin c = new Fixed("c", (t, x) -> igt("c", 15, 16)) {
            @Override
            public ParseResult getResult(TextId text, String content, ParseContext context) {
                received.add(context);
                return super.getResult(text, content, context);
            }
        };
        Text text = TextParser.parse(ID, CONTENT, List.of(a, b, c));
        assertEquals(List.of("a"), received.get(0).claims().stream().map(Claim::grammarId).toList());
        assertEquals(List.of("a", "b"), received.get(1).claims().stream().map(Claim::grammarId).toList());
        assertTrue(received.get(1).isClaimed(19, 25));
        assertFalse(received.get(1).isClaimed(20, 29));
        assertTrue(received.get(1).isClaimed(0, 49));
        // the old getResult still works (default method), and the rule is applied
        assertEquals(2, text.objects().size());
        assertTrue(text.diagnostics("c").get(0).message().contains("Grammar A"), text.diagnostics("c").get(0).message());
    }

    @Test
    void a_failing_grammar_gives_an_internal_error() {
        GrammarPlugin broken = new Fixed("broken", (t, c) -> {
            throw new IllegalStateException("boom");
        });
        GrammarPlugin nothing = new Fixed("null", (t, c) -> null);
        Text text = TextParser.parse(ID, CONTENT, List.of(broken, nothing, new Fixed("a", (t, c) -> igt("a", 1, 2))));
        assertTrue(text.diagnostics("broken").get(0).message().startsWith("Internal error of the grammar"));
        assertTrue(text.diagnostics("broken").get(0).isError());
        assertEquals(1, text.diagnostics("null").size());
        List<CorpusObject> objects = text.objects();
        assertEquals(1, objects.size());
    }

    @Test
    void the_context_answers_without_claims() {
        assertFalse(ParseContext.NONE.isClaimed(0, 100));
        ParseContext context = new ParseContext(List.of(
            new Claim(new TextCoordinate(ID, 20, 40, 1, 1), "a", "a:x"),
            new Claim(new TextCoordinate(ID, 0, 5, 1, 1), "a", "a:x"),
            new Claim(new TextCoordinate(ID, 25, 26, 1, 1), "b", "b:x")));
        assertEquals(0, context.claims().get(0).span().startIndex());
        assertTrue(context.isClaimed(39, 45));
        assertTrue(context.isClaimed(5, 5));
        assertFalse(context.isClaimed(6, 19));
        assertFalse(context.isClaimed(41, 100));
    }
}
