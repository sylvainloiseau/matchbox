package fr.cnrs.lacito.matchbox.grammar.tag;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final TagGrammarPlugin plugin = new TagGrammarPlugin();

    private ParseResult parse(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertSpansNested(r.objects(), text);
        assertKindsPrefixed(r.objects(), TagGrammarPlugin.ID);
        Text t = Text.builder(FILE, text).add(plugin.getId(), r.objects(), r.diagnostics()).build();
        assertEquals(r.objects().size(), t.objects().size(), () -> "invalid spans: " + t.diagnostics());
        return r;
    }

    private static List<String> names(ParseResult r) {
        return r.objects().stream().map(o -> ((Tag) o).getName()).toList();
    }

    @Test
    void tagsAnywhereInALine() {
        String text = "#\"start\" text #\"verb/infinitive\" more #“curly one” end\n'I went' #\" spaced \"\n";
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("start", "verb/infinitive", "curly one", "spaced"), names(r));
        Tag verb = (Tag) r.objects().get(1);
        assertEquals(List.of("verb", "infinitive"), verb.getPath());
        assertCovers("#\"verb/infinitive\"", verb.getTextCoordinate(), text);
        assertCovers("spaced", ((SimpleTag) r.objects().get(3)).field(Tag.TAG).orElseThrow().span(), text);
    }

    @Test
    void whatIsNotATag() {
        String text = """
            # Heading
            ## "quoted heading"
            #hashtag
            # sent_id = s1
            #"unclosed
            #"mixed”
            # "space after hash"
            text with # alone and "quotes"
            """;
        ParseResult r = parse(text);
        assertTrue(r.objects().isEmpty(), () -> names(r).toString());
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
    }

    @Test
    void emptyTagsAreReported() {
        ParseResult r = parse("#\"\" and #\"   \"\n");
        assertTrue(r.objects().isEmpty());
        assertEquals(2, r.diagnostics().size());
    }

    @Test
    void fencedCodeBlocksAreIgnored() {
        // a shorter fence does not close a longer one; a longer fence closes a shorter one
        String text = "#\"before\"\n````\n#\"in code\"\n```\n#\"still code\"\n````\n#\"after\"\n"
            + "~~~\n#\"in tilde\"\n~~~~~\n#\"after tilde\"\n~~~~\n#\"unclosed\"\n";
        assertEquals(List.of("before", "after", "after tilde"), names(parse(text)));
    }

    @Test
    void lineEndings() {
        assertEquals(List.of("a", "b", "c"), names(parse("#\"a\"\r\n```\r\n#\"x\"\r\n```\r#\"b\"\r#\"c\"")));
    }

    @Test
    void renamingWritesStraightQuotes() {
        String text = "x #“old” y\n";
        Tag tag = (Tag) parse(text).objects().get(0);
        var editor = plugin.getObjectEditor();
        EditProposal p = editor.setField(tag, Tag.TAG, " new/name ", text);
        String edited = TextEdit.applyAll(text, assertInstanceOf(EditProposal.Edits.class, p).edits());
        assertEquals("x #\"new/name\" y\n", edited);
        assertEquals(List.of("new/name"), names(parse(edited)));
        assertInstanceOf(EditProposal.Refused.class, editor.setField(tag, Tag.TAG, "", text));
        assertInstanceOf(EditProposal.Refused.class, editor.setField(tag, Tag.TAG, "a\"b", text));
        assertInstanceOf(EditProposal.Refused.class, editor.setField(tag, Tag.TAG, "a\nb", text));
    }

    @Test
    void successiveResultsAndExamples() {
        assertNotSame(plugin.getResult(FILE, "#\"a\"").objects(), plugin.getResult(FILE, "#\"b\"").objects());
        for (GrammarExample e : plugin.getExamples()) {
            ParseResult r = parse(e.text());
            assertTrue(r.diagnostics().isEmpty() && !r.objects().isEmpty(), e.caption());
        }
        assertTrue(plugin.getGrammarSource().contains("lexer grammar TagLexer"));
    }
}
