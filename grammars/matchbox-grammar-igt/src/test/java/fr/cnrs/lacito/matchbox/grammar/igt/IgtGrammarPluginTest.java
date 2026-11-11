package fr.cnrs.lacito.matchbox.grammar.igt;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearTimeline;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearEdits;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IgtGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final IgtGrammarPlugin plugin = new IgtGrammarPlugin();

    static final String FRENCH = """
        Some prose.

        :::{id="1.1", olang="fra", mlang="en", media="O1.WAV", offset="1000"}
        je crois pas qu'elle est assez curieuse
        j'crois pas qu'elle est assez curieuse
        1 90 300 420 610 700
        ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z
        1SG=believe not COMPL=3SG.F be\\3SG.PRES enough curious-FEM
        'I don't think she is curious enough'
        :::

        More prose.
        """;

    private ParseResult parse(String content) {
        ParseResult r = plugin.getResult(FILE, content);
        assertSpansNested(r.objects(), content);
        assertKindsPrefixed(r.objects(), IgtGrammarPlugin.ID);
        Text t = text(content, r);
        assertEquals(r.objects().size(), t.objects().size(), () -> "invalid spans: " + t.diagnostics());
        return r;
    }

    private static Text text(String content, ParseResult r) {
        return Text.builder(FILE, content).add(IgtGrammarPlugin.ID, r.objects(), r.diagnostics()).build();
    }

    private InterlinearGlossedText only(String content) {
        ParseResult r = parse(content);
        assertEquals(1, r.objects().size(), () -> r.diagnostics().toString());
        return (InterlinearGlossedText) r.objects().get(0);
    }

    private static String apply(String content, EditProposal p) {
        assertTrue(p instanceof EditProposal.Edits, () -> p.toString());
        return TextEdit.applyAll(content, ((EditProposal.Edits) p).edits());
    }

    @Test
    void the_french_example() {
        ParseResult r = parse(FRENCH);
        assertTrue(r.diagnostics().isEmpty(), r.diagnostics().toString());
        InterlinearGlossedText igt = (InterlinearGlossedText) r.objects().get(0);
        assertEquals("1.1", igt.metadata().get("id"));
        assertEquals(6, igt.lines().size());
        assertEquals(IgtGrammarPlugin.KIND_ORTHOGRAPHIC, igt.lines().get(0).getKind());
        assertEquals(IgtGrammarPlugin.KIND_LINE, igt.lines().get(1).getKind());
        assertEquals(IgtGrammarPlugin.KIND_TIMELINE, igt.lines().get(2).getKind());
        assertEquals("I don't think she is curious enough", igt.lines().get(5).text());
        assertEquals("ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z", igt.getTranscription());
        assertEquals("1.1", igt.getLabel());
        InterlinearTimeline timeline = (InterlinearTimeline) igt.lines().get(2);
        assertEquals(Optional.of("O1.WAV"), timeline.getMediaReference());
        assertEquals(Optional.of(new TimeRange(1001, 1700)), timeline.getRange(), "to the start of the last word");
        assertTrue(igt.getChildren().stream().anyMatch(c -> c.getKind().equals(IgtGrammarPlugin.KIND_METADATA)));
        InterlinearReading reading = InterlinearReadings.of(text(FRENCH, r)).of(igt).orElseThrow();
        assertEquals(List.of(), reading.issues());
        assertEquals(LineRole.ORTHOGRAPHIC, reading.lines().get(0).role());
    }

    @Test
    void examples_parse_without_diagnostics() {
        for (GrammarExample e : plugin.getExamples()) {
            ParseResult r = parse(e.text());
            assertEquals(1, r.objects().size(), e.caption());
            assertTrue(r.diagnostics().isEmpty(), () -> e.caption() + ": " + r.diagnostics());
            Text t = text(e.text(), r);
            assertEquals(List.of(), InterlinearReadings.of(t).all().iterator().next().issues(), e.caption());
        }
        assertTrue(plugin.getGrammarSource().contains("lexer grammar IgtLexer"));
    }

    @Test
    void what_is_not_an_igt() {
        assertTrue(parse("::: note\ntext\n:::\n").objects().isEmpty(), "a Pandoc div");
        assertTrue(parse("::: {.warning}\ntext\n:::\n").objects().isEmpty(), "a Pandoc div with a class");
        // the bare ::: closing a div is not the start of an IGT; the IGT after it is read
        ParseResult afterDiv = parse("::: ex\nprose\n:::\n\n:::\nʒə pa\n1SG not\n:::\n");
        assertEquals(1, afterDiv.objects().size());
        assertTrue(afterDiv.diagnostics().isEmpty(), afterDiv.diagnostics().toString());
        assertEquals(1, parse("::::\nx\n:::\n").objects().size() + parse("::::\nx\n:::\n").diagnostics().size(),
            "four colons are not a div opener");
        assertTrue(parse("text ::: x\n").objects().isEmpty());
        ParseResult unclosed = parse("prose\n:::\nʒə pa\n1SG not\n");
        assertTrue(unclosed.objects().isEmpty());
        assertEquals(IgtGrammarPlugin.CODE_UNCLOSED, unclosed.diagnostics().get(0).code());
        assertEquals(2, unclosed.diagnostics().get(0).line());
        // a block not closed before the next one
        ParseResult two = parse(":::\nʒə pa\n:::{id=\"2\"}\nʒə pa\n1SG not\n:::\n");
        assertEquals(1, two.objects().size());
        assertEquals("2", ((InterlinearGlossedText) two.objects().get(0)).metadata().get("id"));
    }

    @Test
    void tags_after_a_line_or_alone_belong_to_the_igt() {
        String content = ":::\nʒə pa\n1SG not\n'I do not' #\"neg\"\n#\"todo\"\n:::\n";
        InterlinearGlossedText igt = only(content);
        assertEquals(3, igt.lines().size());
        assertEquals("I do not", igt.lines().get(2).text());
        assertEquals(content.indexOf("'I do not'") + "'I do not'".length() - 1, igt.lines().get(2).getTextCoordinate().stopIndex());
    }

    @Test
    void blank_lines_and_line_endings() {
        InterlinearGlossedText igt = only(":::\r\nʒə=kʁwa pa\r\n\r\n1SG=believe not\r\n:::");
        assertEquals(2, igt.lines().size());
        assertEquals(2, igt.lines().get(1).number());
        assertEquals("1SG=believe not", igt.lines().get(1).text());
    }

    @Test
    void metadata_escaping_errors_and_duplicates() {
        InterlinearGlossedText igt = only(":::{title=\"an \\\"example\\\" \\\\ here\", empty=\"\"}\nx\n:::\n");
        assertEquals("an \"example\" \\ here", igt.metadata().get("title"));
        assertEquals("", igt.metadata().get("empty"));

        ParseResult bad = parse(":::{id=1}\nx\n:::\n");
        assertEquals(1, bad.objects().size(), "still read");
        assertEquals(IgtGrammarPlugin.CODE_METADATA, bad.diagnostics().get(0).code());
        assertEquals(Diagnostic.Category.SYNTAX, bad.diagnostics().get(0).category());

        String dup = ":::{id=\"1\", id=\"2\"}\nx\n:::\n";
        ParseResult d = parse(dup);
        assertEquals("1", ((InterlinearGlossedText) d.objects().get(0)).metadata().get("id"));
        Diagnostic w = d.diagnostics().get(0);
        assertTrue(w.isSemantic() && w.isWarning());
        assertEquals(":::{id=\"1\"}\nx\n:::\n", TextEdit.applyAll(dup, w.fixes().get(0).edits()));
    }

    @Test
    void the_editor_writes_metadata_lines_and_words() {
        String content = ":::{id=\"1\"}\nʒə=kʁwa pa\n1SG=believe not\n'I do not'\n:::\n";
        InterlinearGlossedText igt = only(content);
        var editor = plugin.getObjectEditor();
        assertEquals(":::{id=\"a \\\"b\\\"\"}\nʒə=kʁwa pa\n1SG=believe not\n'I do not'\n:::\n",
            apply(content, editor.setField(igt, "meta/id", "a \"b\"", content)));
        assertEquals(":::{id=\"1\", title=\"T\"}\nʒə=kʁwa pa\n1SG=believe not\n'I do not'\n:::\n",
            apply(content, editor.addField(igt, "meta/title", "T", content)));
        assertEquals(":::{}\nʒə=kʁwa pa\n1SG=believe not\n'I do not'\n:::\n",
            apply(content, editor.removeField(igt, "meta/id", content)));
        assertEquals(content.replace("not\n'", "no\n'"),
            apply(content, editor.setField(igt, InterlinearGlossedText.wordField(1, 1), "no", content)));
        assertEquals(content.replace("I do not", "I don't"),
            apply(content, editor.setField(igt, InterlinearGlossedText.lineField(2), "I don't", content)));
        assertEquals(content.replace("I do not", "I don't"),
            apply(content, editor.setField(igt, InterlinearSentence.FREE_TRANSLATION, "I don't", content)));
        assertTrue(editor.setField(igt, InterlinearGlossedText.wordField(1, 1), "no t", content) instanceof EditProposal.Refused);
        assertTrue(editor.setField(igt, InterlinearGlossedText.lineField(0), "'x'", content) instanceof EditProposal.Refused);
        assertTrue(editor.setField(igt, InterlinearGlossedText.lineField(0), ":::", content) instanceof EditProposal.Refused);
        assertTrue(editor.addField(igt, "meta/a b", "x", content) instanceof EditProposal.Refused);

        String bare = ":::\nʒə pa\n:::\n";
        assertEquals(":::{id=\"1\"}\nʒə pa\n:::\n", apply(bare, editor.addField(only(bare), "meta/id", "1", bare)));
        // every edit parses again cleanly
        String edited = apply(content, editor.addField(igt, "meta/title", "T", content));
        assertTrue(parse(edited).diagnostics().isEmpty());
    }

    private InterlinearReading reading(String content) {
        ParseResult r = parse(content);
        return InterlinearReadings.of(text(content, r)).all().iterator().next();
    }

    @Test
    void insert_delete_and_format() {
        String content = ":::\nje crois pas\n1 120\nʒə=kʁwa pa\n1SG=believe not\n:::\n";
        InterlinearReading r = reading(content);
        assertEquals(List.of(), r.issues());
        String inserted = TextEdit.applyAll(content, InterlinearEdits.insertWord(r, 1));
        assertEquals(":::\nje crois pas\n1 120 120\nʒə=kʁwa ? pa\n1SG=believe ? not\n:::\n", inserted,
            "the orthographic line is not touched");
        assertEquals(List.of(), reading(inserted).issues());
        assertEquals(":::\nje crois pas\n1 120 120\nʒə=kʁwa pa ?\n1SG=believe not ?\n:::\n",
            TextEdit.applyAll(content, InterlinearEdits.insertWord(r, 2)));
        assertEquals(":::\nje crois pas\n1\nʒə=kʁwa\n1SG=believe\n:::\n",
            TextEdit.applyAll(content, InterlinearEdits.deleteWord(r, 1, content)));

        String formatted = TextEdit.applyAll(content, InterlinearEdits.format(r, content));
        assertEquals(":::\nje crois pas\n1           120\nʒə=kʁwa     pa\n1SG=believe not\n:::\n", formatted);
        assertTrue(InterlinearEdits.format(reading(formatted), formatted).isEmpty(), "idempotent");
    }

    @Test
    void display_width() {
        assertEquals(5, fr.cnrs.lacito.matchbox.corpus.igt.DisplayWidth.of("t̠ʃɹ̥æn"));
        assertEquals(4, fr.cnrs.lacito.matchbox.corpus.igt.DisplayWidth.of("漢字"));
        assertEquals(3, fr.cnrs.lacito.matchbox.corpus.igt.DisplayWidth.of("abc"));
    }

    @Test
    void text_actions() {
        String content = ":::\nʒə=kʁwa pa\n1SG=believe not\n:::\n\n:::{}\nʒə pa\n1SG not\n:::\n";
        ParseResult r = parse(content);
        Text t = text(content, r);
        List<TextAction> actions = new IgtTextActions().actionsFor(t, null);
        List<String> first = actions.stream().filter(a -> a.offset() == 0).map(TextAction::label).toList();
        assertTrue(first.contains("Add metadata block"), first.toString());
        assertTrue(first.contains("Declare line roles"));
        assertTrue(first.contains("Number the IGTs of this file"));
        int second = content.indexOf(":::{}");
        assertFalse(actions.stream().anyMatch(a -> a.offset() == second && a.label().equals("Add metadata block")),
            "even {} suppresses it");
        TextAction declare = actions.stream().filter(a -> a.offset() == 0 && a.label().equals("Declare line roles")).findFirst().orElseThrow();
        assertTrue(TextEdit.applyAll(content, declare.edits()).startsWith(":::{setting=\"t,g\"}\n"));
        TextAction number = actions.stream().filter(a -> a.label().startsWith("Number")).findFirst().orElseThrow();
        String numbered = TextEdit.applyAll(content, number.edits());
        assertTrue(numbered.startsWith(":::{id=\"1\"}") && numbered.contains(":::{id=\"2\"}"), numbered);
    }

    @Test
    void fields_lie_inside_the_igt() {
        InterlinearGlossedText igt = only(FRENCH);
        CorpusObject o = igt;
        assertTrue(((HasFields) o).field(InterlinearGlossedText.wordField(3, 0)).isPresent());
        assertEquals("ʒə=kʁwa", ((HasFields) o).field(InterlinearGlossedText.wordField(3, 0)).orElseThrow().value());
    }

    @Test
    void two_thousand_igts_are_parsed_and_read_quickly() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            s.append(":::{id=\"").append(i).append("\"}\nʒə=kʁwa pa k=ɛl\n1SG=believe not COMPL=3SG.F\n'I think not'\n1 2 3\n:::\n\n");
        }
        String content = s.toString();
        long t0 = System.nanoTime();
        ParseResult r = plugin.getResult(FILE, content);
        Text t = text(content, r);
        InterlinearReadings.of(t);
        new IgtTextActions().actionsFor(t, null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(2000, r.objects().size());
        assertTrue(ms < 10_000, "took " + ms + " ms");
    }
}
