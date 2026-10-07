package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarkdownHeadingGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final MarkdownHeadingGrammarPlugin plugin = new MarkdownHeadingGrammarPlugin();

    private ParseResult parse(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertSpansNested(r.objects(), text);
        assertKindsPrefixed(r.objects(), MarkdownHeadingGrammarPlugin.ID);
        return r;
    }

    private static List<MarkdownSection> sections(ParseResult r) {
        return r.objects().stream().filter(MarkdownSection.class::isInstance).map(MarkdownSection.class::cast).toList();
    }

    /** Titles of all sections, depth-first, with their levels: "1:Session 1". */
    private static List<String> outline(ParseResult r) {
        List<String> out = new ArrayList<>();
        sections(r).forEach(s -> collect(s, out));
        return out;
    }

    private static void collect(MarkdownSection s, List<String> out) {
        out.add(s.getLevel() + ":" + s.getTitle());
        s.getContent().forEach(c -> collect(c, out));
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static String sample() throws IOException {
        try (InputStream in = MarkdownHeadingGrammarPluginTest.class.getResourceAsStream("/sample.md")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void sampleFile() throws IOException {
        String text = sample();
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        TextHeader header = (TextHeader) r.objects().get(0);
        assertEquals(map("language", "Tuwari", "speaker", "Elaisa"), header.getProperties());
        assertEquals(List.of("1:Session 1", "2:Nouns of the -mo class", "3:Examples", "2:Verbs", "1:Session 2"), outline(r));
        MarkdownSection session1 = sections(r).get(0);
        assertEquals(MarkdownSection.Style.SETEXT, session1.getStyle());
        assertEquals(map("date", "2024-05-12T14:30", "media", "recordings/session1.wav", "place", "Tuwari village"),
            session1.getMetadata());
        assertTrue(text.substring(session1.getTextCoordinate().startIndex()).startsWith("Session 1"));
        assertTrue(text.substring(session1.getTextCoordinate().stopIndex() + 1).startsWith("# Session 2"));
        assertCovers("Tuwari village", session1.field(Section.metadataField("place")).orElseThrow().span(), text);
        assertCovers("Session 1", session1.field(Section.TITLE).orElseThrow().span(), text);
        assertEquals(2, session1.getContent().size(), "the fence hides '# not a heading'");
    }

    @Test
    void atxHeadings() {
        String text = """
            # A #
            #hashtag
            ####### seven
                # indented code
            ###   C   ###
            #
            ## B
            """;
        ParseResult r = parse(text);
        assertEquals(List.of("1:A", "3:C", "1:", "2:B"), outline(r));
        MarkdownSection c = sections(r).get(0).getContent().get(0);
        assertCovers("C", c.field(Section.TITLE).orElseThrow().span(), text);
        assertTrue(sections(r).get(1).field(Section.TITLE).orElseThrow().span().isEmpty());
    }

    @Test
    void sectionsEndBeforeTheNextHeadingOfTheSameOrHigherLevel() {
        String text = "intro\n# A\n### A1\n## A2\n# B\nend";
        ParseResult r = parse(text);
        assertEquals(List.of("1:A", "3:A1", "2:A2", "1:B"), outline(r));
        MarkdownSection a = sections(r).get(0);
        assertEquals("# A\n### A1\n## A2\n", text.substring(a.getTextCoordinate().startIndex(), a.getTextCoordinate().stopIndex() + 1));
        MarkdownSection b = sections(r).get(1);
        assertEquals(text.length() - 1, b.getTextCoordinate().stopIndex());
    }

    @Test
    void setextHeadingsOnlyAfterASingleTextLine() {
        String text = """
            Title
            =====

            Sub
            ---

            two lines
            of text
            ---

            :::
            a mio
            1sg
            'I went'
            :::
            ---

            ===
            """;
        ParseResult r = parse(text);
        assertEquals(List.of("1:Title", "2:Sub"), outline(r));
        assertTrue(r.diagnostics().isEmpty());
    }

    @Test
    void metadataAfterAHeadingWithBlankLinesAndHeaderOnlyOnLineOne() {
        String text = """

            ---
            not: header
            ---
            # A


            ---
            k: v
            ---
            text
            ---
            """;
        ParseResult r = parse(text);
        assertTrue(r.objects().stream().noneMatch(TextHeader.class::isInstance), "header only on line 1");
        // a single text line followed by "---" is a Setext heading (as in CommonMark)
        assertEquals(List.of("2:not: header", "1:A", "2:text"), outline(r));
        assertEquals(map("k", "v"), sections(r).get(1).getMetadata());
    }

    @Test
    void unclosedBlockIsOrdinaryText() {
        String text = "# A\n---\nk: v\n\n# B\n---\nx: y\n---\n";
        ParseResult r = parse(text);
        assertEquals(List.of("1:A", "1:B"), outline(r));
        assertTrue(sections(r).get(0).getMetadata().isEmpty());
        assertEquals(map("x", "y"), sections(r).get(1).getMetadata());
        // without a blank line before it, a "#" line in a block is a YAML comment
        ParseResult comment = parse("# A\n---\nk: v\n# a comment\n---\n");
        assertEquals(List.of("1:A"), outline(comment));
        assertEquals(map("k", "v"), sections(comment).get(0).getMetadata());
    }

    @Test
    void fencesHideHeadingsUnderlinesAndBlocks() {
        String text = "# A\n~~~~\n# no\n~~~\n---\nk: v\n---\n~~~~\n## B\n````\n# not closed\n";
        ParseResult r = parse(text);
        assertEquals(List.of("1:A", "2:B"), outline(r));
        assertTrue(sections(r).get(0).getMetadata().isEmpty());
    }

    @Test
    void yamlValues() {
        String text = """
            ---
            a: "x \\"y\\" z"
            b: 'it''s'
            c:
            d: plain # comment
            e: "unclosed
            # a comment line

            a: again
            - item
              nested: no
            f: |
            ---
            """;
        ParseResult r = parse(text);
        SimpleTextHeader h = (SimpleTextHeader) r.objects().get(0);
        assertEquals(map("b", "it's", "c", "", "d", "plain", "e", "\"unclosed", "a", "again"), h.getProperties());
        assertTrue(h.field(TextHeader.propertyField("c")).orElseThrow().span().isEmpty());
        assertCovers("plain", h.field(TextHeader.propertyField("d")).orElseThrow().span(), text);
        List<String> messages = r.diagnostics().stream().map(d -> d.message()).toList();
        assertEquals(4, messages.size(), messages::toString);
        assertTrue(messages.get(0).startsWith("Duplicate key 'a'"));
        assertTrue(messages.get(1).startsWith("Lists"));
        assertTrue(messages.get(2).startsWith("Nested"));
        assertTrue(messages.get(3).startsWith("Multi-line"));
    }

    @Test
    void lineEndingsAndEdgeCases() {
        String crlf = "---\r\nk: v\r\n---\r\n# A\r\nTitle\r\n=====\r\n";
        ParseResult r = parse(crlf);
        assertEquals(map("k", "v"), ((TextHeader) r.objects().get(0)).getProperties());
        assertEquals(List.of("1:A", "1:Title"), outline(r));
        assertEquals(List.of("1:A", "2:B"), outline(parse("# A\r## B\r")));
        assertEquals(List.of("1:A"), outline(parse("# A")));
        assertTrue(parse("").objects().isEmpty());
        ParseResult headerOnly = parse("---\n---\n");
        assertEquals(1, headerOnly.objects().size());
        assertTrue(((TextHeader) headerOnly.objects().get(0)).getProperties().isEmpty());
    }

    @Test
    void successiveResultsAreIndependent() throws IOException {
        ParseResult first = plugin.getResult(FILE, sample());
        ParseResult second = plugin.getResult(FILE, "# Other\n");
        assertNotSame(first.objects(), second.objects());
        assertEquals(List.of("1:Other"), outline(second));
        assertEquals(5, outline(first).size());
    }

    @Test
    void metadataAppliesToObjectsOfOtherGrammars() throws IOException {
        String text = sample();
        ParseResult r = plugin.getResult(FILE, text);
        int at = text.indexOf("<mami>");
        SimpleIGT other = new SimpleIGT("other:igt", new TextCoordinate(FILE, at, at + 5, 20, 20), "x", "", "");
        Text t = Text.builder(FILE, text)
            .add(plugin.getId(), r.objects(), r.diagnostics())
            .add("other", List.of(other), List.of())
            .build();
        assertEquals(List.of("Session 1", "Nouns of the -mo class"),
            t.enclosingSections(other).stream().map(Section::getTitle).toList());
        Map<String, String> metadata = t.metadataOf(other);
        assertEquals("Tuwari", metadata.get("language"));
        assertEquals("2024-05-12T14:30", metadata.get("date"));
    }

    @Test
    void examplesParseWithoutDiagnostics() {
        for (GrammarExample e : plugin.getExamples()) {
            ParseResult r = parse(e.text());
            assertTrue(r.diagnostics().isEmpty(), e.caption());
            assertFalse(r.objects().isEmpty(), e.caption());
        }
        assertTrue(plugin.getGrammarSource().contains("lexer grammar MarkdownHeadingLexer"));
    }

    @Test
    void addHeaderActionOnlyWithoutHeader() {
        MarkdownHeadingActions actions = new MarkdownHeadingActions();
        String text = "# A\n";
        Text without = Text.builder(FILE, text).add(plugin.getId(), parse(text).objects(), List.of()).build();
        var offered = actions.actionsFor(without, null);
        assertEquals(1, offered.size());
        String edited = offered.get(0).edits().get(0).apply(text);
        ParseResult after = parse(edited);
        assertTrue(after.objects().get(0) instanceof TextHeader);
        assertEquals(List.of("1:A"), outline(after));
        Text with = Text.builder(FILE, edited).add(plugin.getId(), after.objects(), List.of()).build();
        assertTrue(actions.actionsFor(with, null).isEmpty());
        Text otherGrammar = Text.builder(FILE, text).add("other", List.<CorpusObject>of(), List.of()).build();
        assertTrue(actions.actionsFor(otherGrammar, null).isEmpty());
    }
}
