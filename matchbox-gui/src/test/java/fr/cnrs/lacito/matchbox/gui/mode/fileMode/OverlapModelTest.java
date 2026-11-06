package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure models of the overlaps: rows of the Overlaps tab, section band of the margin. */
class OverlapModelTest {

    private static final String CONTENT = "line\n".repeat(20);

    /** Offsets of line {@code n} (0-based); each line is 5 characters. */
    private static TextCoordinate lines(TextId id, int first, int last) {
        return new TextCoordinate(id, first * 5, last * 5 + 3, first + 1, last + 1);
    }

    private static Text text(TextId id) {
        return Text.builder(id, CONTENT)
            .add("a", "Grammar A", List.of(new SimpleIGT("a:igt", lines(id, 2, 4), "t", "m", "f"),
                new SimpleIGT("a:igt", lines(id, 10, 11), "t", "m", "f")), List.of())
            .add("b", "Grammar B", List.of(new SimpleIGT("b:igt", lines(id, 10, 11), "t", "m", "f"),
                new SimpleIGT("b:igt", lines(id, 3, 3), "t", "m", "f")), List.of())
            .build();
    }

    @Test
    void rows_by_file_then_line() {
        TextId b = TextId.of(Path.of("B.md"));
        TextId a = TextId.of(Path.of("a.md"));
        Corpus corpus = new Corpus();
        corpus.put(text(b));
        corpus.put(text(a));
        List<OverlapRows.Row> rows = OverlapRows.of(corpus, id -> id.equals("a") ? "Grammar A" : "Grammar B");
        // FileOrder: a.md before B.md, case ignored; then by line
        assertEquals(List.of("a.md", "a.md", "B.md", "B.md"), rows.stream().map(OverlapRows.Row::fileName).toList());
        assertEquals(List.of(4, 11, 4, 11), rows.stream().map(OverlapRows.Row::line).toList());
        OverlapRows.Row first = rows.get(0);
        assertEquals("Not kept", first.outcome());
        assertEquals("igt — Grammar B, line 4", first.reading());
        assertEquals("igt — Grammar A, lines 3–5", first.other());
        // Show both: the kept reading is selected, the other outlined
        assertSame(first.conflict().other(), first.selected());
        assertEquals(first.conflict().object().getTextCoordinate(), first.outlined());
        assertEquals(4, OverlapRows.counts(corpus)[0]);
        assertEquals(4, OverlapRows.counts(corpus)[1]);
        assertEquals("Overlaps (4)", OverlapRows.tabTitle(4));
        assertEquals("Overlaps", OverlapRows.tabTitle(0));
    }

    @Test
    void the_band_is_the_innermost_section_at_the_caret() {
        TextId id = TextId.of(Path.of("s.md"));
        SimpleSection sub = new SimpleSection("m:section", lines(id, 5, 9), "Sub", Map.of(), List.of());
        SimpleSection top = new SimpleSection("m:section", new TextCoordinate(id, 0, CONTENT.length() - 1, 1, 20),
            "Top", Map.of(), List.of(sub));
        Text text = Text.builder(id, CONTENT).add("m", List.of(top), List.of()).build();
        assertEquals(Optional.of(new SectionBand.Lines(0, 19)), SectionBand.linesOf(text, 2));
        assertEquals(Optional.of(new SectionBand.Lines(5, 9)), SectionBand.linesOf(text, 6 * 5));
        // end of file: in the last section
        assertEquals(Optional.of(new SectionBand.Lines(0, 19)), SectionBand.linesOf(text, CONTENT.length()));
        Text none = Text.builder(id, CONTENT).build();
        assertTrue(SectionBand.linesOf(none, 3).isEmpty());
        assertTrue(new SectionBand.Lines(5, 9).contains(9));
    }
}
