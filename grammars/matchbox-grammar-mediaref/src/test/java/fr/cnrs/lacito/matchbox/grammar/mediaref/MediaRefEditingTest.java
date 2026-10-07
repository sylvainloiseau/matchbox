package fr.cnrs.lacito.matchbox.grammar.mediaref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Each edit is applied, the text parsed again, and the new values read back. */
class MediaRefEditingTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final MediaRefGrammarPlugin plugin = new MediaRefGrammarPlugin();
    private final ObjectEditor editor = plugin.getObjectEditor();

    private MediaMarker marker(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics() + " in " + text);
        return (MediaMarker) r.objects().get(0);
    }

    /** Applies the edit and returns the new text. */
    private String set(String text, String field, String value) {
        EditProposal p = editor.setField(marker(text), field, value, text);
        return TextEdit.applyAll(text, assertInstanceOf(EditProposal.Edits.class, p, () -> p.toString()).edits());
    }

    private void refused(String text, String field, String value) {
        assertInstanceOf(EditProposal.Refused.class, editor.setField(marker(text), field, value, text));
    }

    @Test
    void timesKeepTheirFormat() {
        assertEquals("@media a.wav 12100-15500\n", set("@media a.wav 12000-15500\n", MediaSegment.START, "12100"));
        assertEquals("@media a.wav 0:12.1-0:15\n", set("@media a.wav 0:12-0:15\n", MediaSegment.START, "12100"));
        assertEquals("@media 0:12-0:16.5 x\n", set("@media 0:12-0:15 x\n", MediaSegment.END, "0:16.5"));
        refused("@media a.wav 0:12-0:15\n", MediaSegment.END, "0:11");
        refused("@media a.wav 0:12-0:15\n", MediaSegment.START, "soon");
    }

    @Test
    void ranges() {
        String added = set("@media a.wav greeting\n", MediaSegment.RANGE, "12000-15500");
        assertEquals("@media a.wav 0:12.000-0:15.500 greeting\n", added);
        assertEquals(Optional.of(new TimeRange(12000, 15500)), marker(added).getRange());
        assertEquals("@media a.wav greeting\n", set("@media a.wav 0:12-0:15 greeting\n", MediaSegment.RANGE, ""));
        assertEquals("@media a.wav 0:01.000-0:02.000\n", set("@media a.wav 0:12-0:15\n", MediaSegment.RANGE, "1000-2000"));
        refused("@media 0:12-0:15\n", MediaSegment.RANGE, "");
        refused("@media a.wav\n", MediaSegment.RANGE, "15-12");
    }

    @Test
    void files() {
        assertEquals("@media b.wav 0-5\n", set("@media a.wav 0-5\n", MediaSegment.MEDIA, "b.wav"));
        assertEquals("@media \"my file.wav\" 0-5\n", set("@media a.wav 0-5\n", MediaSegment.MEDIA, "my file.wav"));
        String quoted = set("@media 0-5\n", MediaSegment.MEDIA, "1-2");
        assertEquals("@media \"1-2\" 0-5\n", quoted);
        assertEquals(Optional.of("1-2"), marker(quoted).getMediaReference());
        assertEquals("@media 0-5\n", set("@media \"my file.wav\" 0-5\n", MediaSegment.MEDIA, ""));
        assertEquals("@media b.wav 0-5\n", set("@media \"my file.wav\" 0-5\n", MediaSegment.MEDIA, "b.wav"));
        refused("@media a.wav\n", MediaSegment.MEDIA, "");
    }

    @Test
    void captions() {
        assertEquals("@media a.wav 0-5 hello\n", set("@media a.wav 0-5\n", MediaSegment.CAPTION, "hello"));
        assertEquals("@media a.wav 0-5 bye\n", set("@media a.wav 0-5 hello\n", MediaSegment.CAPTION, "bye"));
        assertEquals("@media a.wav 0-5\n", set("@media a.wav 0-5 hello\n", MediaSegment.CAPTION, ""));
        assertEquals("@media a.wav hello", set("@media a.wav", MediaSegment.CAPTION, "hello"));
        refused("@media a.wav\n", MediaSegment.CAPTION, "1-2 later");
        refused("@media a.wav\n", MediaSegment.CAPTION, "a\nb");
    }
}
