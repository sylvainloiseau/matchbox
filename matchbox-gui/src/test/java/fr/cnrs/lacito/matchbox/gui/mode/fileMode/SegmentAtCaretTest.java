package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SegmentAtCaretTest {

    /** A media segment of another grammar. */
    public static final class Segment extends AbstractCorpusObject implements MediaSegment {
        public Segment(TextCoordinate span) {
            super("other:segment", span);
        }
        public Optional<String> getMediaReference() { return Optional.of("a.wav"); }
        public Optional<TimeRange> getRange() { return Optional.empty(); }
    }

    private static final TextId T = new TextId(Path.of("t.md"));

    @Test
    void onTheLineThenAboveInTheSameSection() {
        String content = """
            # A
            @media 0-5 first
            <mami> {pig}
            :::
            a mio
            :::
            # B
            text without marker
            @media 5-9 second
            """;
        ParseResult markers = new MediaRefGrammarPlugin().getResult(T, content);
        int a = content.indexOf("# A");
        int b = content.indexOf("# B");
        SimpleSection sectionA = new SimpleSection("s:section", new TextCoordinate(T, a, b - 1, 1, 6), "A", Map.of(), List.of());
        SimpleSection sectionB = new SimpleSection("s:section", new TextCoordinate(T, b, content.length() - 1, 7, 9), "B", Map.of(), List.of());
        Text text = Text.builder(T, content)
            .add("s", List.of(sectionA, sectionB), List.of())
            .add("mediaref-grammar", markers.objects(), markers.diagnostics())
            .build();

        assertEquals("first", find(text, content.indexOf("first")));
        assertEquals("first", find(text, content.indexOf("@media 0-5")), "start of the marker line");
        assertEquals("first", find(text, content.indexOf("mio")), "below the marker, same section");
        assertTrue(SegmentAtCaret.find(text, content.indexOf("without")).isEmpty(), "not across a heading");
        assertEquals("second", find(text, content.length() - 1));
    }

    private static String find(Text text, int offset) {
        return SegmentAtCaret.find(text, offset).map(MediaSegment::getCaption).orElse(null);
    }
}
