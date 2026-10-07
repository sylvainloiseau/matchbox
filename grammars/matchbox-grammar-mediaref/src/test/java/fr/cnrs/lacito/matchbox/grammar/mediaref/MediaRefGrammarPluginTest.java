package fr.cnrs.lacito.matchbox.grammar.mediaref;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaRefGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final MediaRefGrammarPlugin plugin = new MediaRefGrammarPlugin();

    private ParseResult parse(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertSpansNested(r.objects(), text);
        assertKindsPrefixed(r.objects(), MediaRefGrammarPlugin.ID);
        // field spans are checked by Text.Builder
        Text t = Text.builder(FILE, text).add(plugin.getId(), r.objects(), r.diagnostics()).build();
        assertEquals(r.objects().size(), t.objects().size(), () -> "invalid spans: " + t.diagnostics());
        return r;
    }

    private static List<MediaMarker> markers(ParseResult r) {
        return r.objects().stream().map(MediaMarker.class::cast).toList();
    }

    private static MediaMarker only(ParseResult r) {
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(1, r.objects().size());
        return markers(r).get(0);
    }

    @Test
    void theThreeForms() {
        MediaMarker both = only(parse("@media recordings/s1.wav 12000-15500\n"));
        assertEquals(Optional.of("recordings/s1.wav"), both.getMediaReference());
        assertEquals(Optional.of(new TimeRange(12000, 15500)), both.getRange());
        MediaMarker rangeOnly = only(parse("@media 0:12.000-0:15.500\n"));
        assertTrue(rangeOnly.getMediaReference().isEmpty());
        assertEquals(Optional.of(new TimeRange(12000, 15500)), rangeOnly.getRange());
        MediaMarker fileOnly = only(parse("@media recordings/jaflo.wav\n"));
        assertTrue(fileOnly.isWholeMedia());
        assertEquals("whole file — jaflo.wav", fileOnly.getLabel());
    }

    @Test
    void captions() {
        String text = "@media 0:12-0:15.5 greeting, part 2 \n@media rec.wav pronunciation of jaflo\n"
            + "@media rec.wav 1-2 3-4\n";
        List<MediaMarker> m = markers(parse(text));
        assertEquals("greeting, part 2", m.get(0).getCaption());
        assertEquals("greeting, part 2", m.get(0).getLabel());
        assertCovers("greeting, part 2", m.get(0).field(MediaSegment.CAPTION).orElseThrow().span(), text);
        assertEquals("pronunciation of jaflo", m.get(1).getCaption());
        assertTrue(m.get(1).isWholeMedia());
        assertEquals(Optional.of(new TimeRange(1, 2)), m.get(2).getRange());
        assertEquals("3-4", m.get(2).getCaption(), "only the first range is the range");
    }

    @Test
    void timesAndSeparators() {
        String text = "@media 1:02:03.250 – 1:02:07\n@media rec.wav 0:12.5 - 15500\n";
        List<MediaMarker> m = markers(parse(text));
        assertEquals(Optional.of(new TimeRange(3723250, 3727000)), m.get(0).getRange());
        assertEquals(Optional.of(new TimeRange(12500, 15500)), m.get(1).getRange());
        assertCovers("1:02:03.250", m.get(0).field(MediaSegment.START).orElseThrow().span(), text);
        assertCovers("1:02:07", m.get(0).field(MediaSegment.END).orElseThrow().span(), text);
    }

    @Test
    void fileNames() {
        String text = "@media \"field trip/s1 \\\"a\\\".wav\" 0-5\n@media session-2024-05.wav 0-5\n"
            + "@media \"1-2\"\n@media 1-2\n@media file:/tmp/a.wav\n";
        List<MediaMarker> m = markers(parse(text));
        assertEquals(Optional.of("field trip/s1 \"a\".wav"), m.get(0).getMediaReference());
        assertEquals(Optional.of("session-2024-05.wav"), m.get(1).getMediaReference());
        assertEquals(Optional.of("1-2"), m.get(2).getMediaReference(), "quoted: a file");
        assertTrue(m.get(3).getMediaReference().isEmpty(), "unquoted: a range");
        assertEquals(Optional.of("file:/tmp/a.wav"), m.get(4).getMediaReference());
    }

    @Test
    void invalidMarkersAreReported() {
        for (String bad : List.of("@media\n", "@media 0:75-0:80\n", "@media 15500-12000\n", "@media   \n")) {
            ParseResult r = plugin.getResult(FILE, bad);
            assertTrue(r.objects().isEmpty(), bad);
            assertFalse(r.diagnostics().isEmpty(), bad);
        }
    }

    @Test
    void otherNotationsAreIgnored() {
        String text = """
            ---
            media: recordings/s1.wav
            ---
            # Session 1
            <mami> {pig}
            :::
            a mio
            :::
            # sent_id = s1
            1	The	the	DET	_	_	2	det	_	_
            @mediafile not a marker
            @other 12-15
             @media indented 1-2
            @media 0-5
            """;
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(1, r.objects().size());
    }

    @Test
    void lineEndings() {
        List<MediaMarker> m = markers(parse("@media a.wav 0-5\r\n@media b.wav\r@media 1-2 last"));
        assertEquals(3, m.size());
        assertEquals("last", m.get(2).getCaption());
        assertEquals(Optional.of("b.wav"), m.get(1).getMediaReference());
    }

    @Test
    void successiveResultsAndExamples() {
        ParseResult first = plugin.getResult(FILE, "@media a.wav\n");
        ParseResult second = plugin.getResult(FILE, "@media b.wav\n");
        assertNotSame(first.objects(), second.objects());
        for (GrammarExample e : plugin.getExamples()) {
            only(parse(e.text()));
        }
        assertTrue(plugin.getGrammarSource().contains("lexer grammar MediaRefLexer"));
    }
}
