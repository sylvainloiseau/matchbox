package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each edit is applied, the text parsed again, and the new value read back. */
class MarkdownHeadingEditingTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final MarkdownHeadingGrammarPlugin plugin = new MarkdownHeadingGrammarPlugin();
    private final ObjectEditor editor = plugin.getObjectEditor();

    private static final String TEXT = """
        ---
        speaker: Elaisa
        ---

        # Session 1

        ---
        date: 2024-05-12
        empty:
        ---

        Nouns
        -----

        #
        """;

    private ParseResult parse(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics() + "\n" + text);
        return r;
    }

    private TextHeader header(ParseResult r) {
        return (TextHeader) r.objects().get(0);
    }

    private MarkdownSection session(ParseResult r) {
        return (MarkdownSection) r.objects().get(1);
    }

    private MarkdownSection nouns(ParseResult r) {
        return session(r).getContent().get(0);
    }

    private MarkdownSection empty(ParseResult r) {
        return (MarkdownSection) r.objects().get(2);
    }

    private String apply(EditProposal p, String text) {
        return TextEdit.applyAll(text, assertInstanceOf(EditProposal.Edits.class, p).edits());
    }

    private void refused(EditProposal p) {
        assertInstanceOf(EditProposal.Refused.class, p);
    }

    @Test
    void titles() {
        ParseResult r = parse(TEXT);
        String t1 = apply(editor.setField(session(r), Section.TITLE, "First session", TEXT), TEXT);
        assertEquals("First session", session(parse(t1)).getTitle());
        String t2 = apply(editor.setField(nouns(r), Section.TITLE, "Nouns and verbs", TEXT), TEXT);
        assertEquals("Nouns and verbs", nouns(parse(t2)).getTitle());
        assertEquals(MarkdownSection.Style.SETEXT, nouns(parse(t2)).getStyle());
        String t3 = apply(editor.setField(empty(r), Section.TITLE, "Later", TEXT), TEXT);
        assertEquals("Later", empty(parse(t3)).getTitle());
        refused(editor.setField(session(r), Section.TITLE, "A #", TEXT));
        refused(editor.setField(session(r), Section.TITLE, "a\nb", TEXT));
        refused(editor.setField(nouns(r), Section.TITLE, "", TEXT));
        refused(editor.setField(nouns(r), Section.TITLE, "# x", TEXT));
    }

    @Test
    void values() {
        ParseResult r = parse(TEXT);
        String date = Section.metadataField("date");
        String t1 = apply(editor.setField(session(r), date, "2024-06-01T09:00", TEXT), TEXT);
        assertEquals("2024-06-01T09:00", session(parse(t1)).getMetadata().get("date"));
        for (String value : List.of("a: b", "#hash", " spaced ", "say \"hi\"", "")) {
            String t = apply(editor.setField(session(r), date, value, TEXT), TEXT);
            assertEquals(value, session(parse(t)).getMetadata().get("date"), value);
            // and back to a plain value from the quoted one
            ParseResult again = parse(t);
            String back = apply(editor.setField(session(again), date, "plain", t), t);
            assertEquals("plain", session(parse(back)).getMetadata().get("date"));
        }
        String t2 = apply(editor.setField(session(r), Section.metadataField("empty"), "filled", TEXT), TEXT);
        assertEquals("filled", session(parse(t2)).getMetadata().get("empty"));
        String t3 = apply(editor.setField(header(r), TextHeader.propertyField("speaker"), "John", TEXT), TEXT);
        assertEquals("John", header(parse(t3)).getProperties().get("speaker"));
        refused(editor.setField(session(r), date, "a\nb", TEXT));
    }

    @Test
    void addingEntries() {
        ParseResult r = parse(TEXT);
        String t1 = apply(editor.addField(header(r), TextHeader.propertyField("language"), "Tuwari", TEXT), TEXT);
        assertEquals(List.of("speaker", "language"), List.copyOf(header(parse(t1)).getProperties().keySet()));
        String t2 = apply(editor.addField(session(r), Section.metadataField("media"), "rec/a.wav", TEXT), TEXT);
        assertEquals("rec/a.wav", session(parse(t2)).getMetadata().get("media"));
        // sections without a block: a block is created after the heading
        String t3 = apply(editor.addField(nouns(r), Section.metadataField("speakers"), "Elaisa, John", TEXT), TEXT);
        assertEquals("Elaisa, John", nouns(parse(t3)).getMetadata().get("speakers"));
        assertEquals("Nouns", nouns(parse(t3)).getTitle());
        String t4 = apply(editor.addField(empty(r), Section.metadataField("k"), "", TEXT), TEXT);
        assertEquals("", empty(parse(t4)).getMetadata().get("k"));
        refused(editor.addField(session(r), Section.metadataField("date"), "x", TEXT));
        refused(editor.addField(session(r), Section.metadataField("a:b"), "x", TEXT));
        refused(editor.addField(session(r), Section.metadataField(""), "x", TEXT));
        refused(editor.addField(session(r), Section.TITLE, "x", TEXT));
    }

    @Test
    void addingToAHeadingOnTheLastLineWithoutLineEnding() {
        String text = "# A";
        ParseResult r = parse(text);
        String t = apply(editor.addField(r.objects().get(0), Section.metadataField("k"), "v", text), text);
        assertEquals("v", ((MarkdownSection) parse(t).objects().get(0)).getMetadata().get("k"));
    }

    @Test
    void removingEntries() {
        ParseResult r = parse(TEXT);
        String t1 = apply(editor.removeField(session(r), Section.metadataField("date"), TEXT), TEXT);
        assertEquals(List.of("empty"), List.copyOf(session(parse(t1)).getMetadata().keySet()));
        String t2 = apply(editor.removeField(header(r), TextHeader.propertyField("speaker"), TEXT), TEXT);
        ParseResult emptied = parse(t2);
        assertTrue(header(emptied).getProperties().isEmpty(), "the emptied block is kept");
        refused(editor.removeField(session(r), Section.TITLE, TEXT));
    }

    @Test
    void crlfTextsKeepTheirLineEndings() {
        String text = TEXT.replace("\n", "\r\n");
        ParseResult r = parse(text);
        CorpusObject nouns = nouns(r);
        String t = apply(editor.addField(nouns, Section.metadataField("k"), "v", text), text);
        assertTrue(!t.replace("\r\n", "").contains("\n"), "no bare \\n inserted");
        assertEquals("v", nouns(parse(t)).getMetadata().get("k"));
    }
}
