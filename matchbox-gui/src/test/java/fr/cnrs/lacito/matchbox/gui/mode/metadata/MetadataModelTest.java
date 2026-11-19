package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleDependencyTree;
import fr.cnrs.lacito.matchbox.grammar.igt.IgtGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.TextParser;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.ColumnFilter.Cells;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.ColumnFilter.Match;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The model of the Metadata mode: rows, columns, filters, cell edits, order. */
class MetadataModelTest {

    static final String NOTES = """
        ---
        speaker: Ann
        date: 2024-05-12
        ---

        # Session 1

        ---
        speaker: Bob
        Media: a.wav
        ---

        :::{id="1", media="b.wav"}
        a b
        c d
        :::

        # Session 2

        prose
        """;

    static final TextId A = new TextId(Path.of("a.md"));
    static final TextId B = new TextId(Path.of("b.md"));

    static Text parse(TextId id, String content) {
        return TextParser.parse(id, content, List.of(new MarkdownHeadingGrammarPlugin(), new IgtGrammarPlugin()));
    }

    private static MetadataIndex index(boolean headers) {
        MetadataIndex index = new MetadataIndex(t -> headers);
        index.update(B, Optional.of(parse(B, "# Only a title\n")));
        index.update(A, Optional.of(parse(A, NOTES)));
        return index;
    }

    private static List<String> describe(List<MetadataRow> rows) {
        return rows.stream().map(r -> r.text().displayName() + " " + r.type() + " " + r.label() + " " + r.line()
            + (r.withoutBlock() ? " +" : "")).toList();
    }

    @Test
    void rowsInFileThenTextOrderWithTheObjectsWithoutBlock() {
        assertEquals(List.of("a.md Header — 1", "a.md Section Session 1 8", "a.md IGT 1 13", "a.md Section Session 2 18 +",
            "b.md Header — 1 +", "b.md Section Only a title 1 +"), describe(index(true).all()));
        assertEquals(5, index(false).all().size(), "no header candidate when no grammar can write one");
    }

    @Test
    void updatingATextReplacesOnlyItsRows() {
        MetadataIndex index = index(true);
        index.update(B, Optional.empty());
        assertTrue(index.all().stream().allMatch(r -> r.text().equals(A)));
        index.update(A, Optional.of(parse(A, "---\nk: v\n---\n")));
        assertEquals(List.of("a.md Header — 1"), describe(index.all()));
    }

    @Test
    void columnsAreTheUnionOfTheKeysWithTheirCounts() {
        MetadataColumns columns = MetadataColumns.of(index(true).all());
        assertEquals(List.of("speaker", "date", "Media", "id", "media"), List.copyOf(columns.keys()));
        assertEquals(2, columns.count("speaker"));
        assertEquals(List.of("media"), columns.caseVariants("Media"));
        assertEquals(List.of("speaker", "date", "Media", "id", "media"),
            columns.ordered(MetadataColumns.Order.FREQUENCY, List.of()), "ties keep the first appearance");
        assertEquals(List.of("date", "id", "Media", "media", "speaker"),
            columns.ordered(MetadataColumns.Order.ALPHABETIC, List.of()), "case ignored, then exact (M < m)");
        assertEquals(List.of("id", "speaker", "date", "Media", "media"),
            columns.ordered(MetadataColumns.Order.FIRST_APPEARANCE, List.of("id", "gone")), "moved columns first");
    }

    @Test
    void columnFilters() {
        Optional<String> ann = Optional.of("Ann Smith");
        Optional<String> none = Optional.empty();
        assertTrue(ColumnFilter.NONE.test(none));
        assertTrue(ColumnFilter.NONE.withText("ann").test(ann), "contains, case ignored");
        assertFalse(ColumnFilter.NONE.withText("ann").withCaseSensitive(true).test(ann));
        assertFalse(ColumnFilter.NONE.withText("Ann").withMatch(Match.EQUALS).test(ann));
        assertTrue(ColumnFilter.NONE.withText("^ann\\b").withMatch(Match.REGEX).test(ann));
        assertTrue(ColumnFilter.NONE.withCells(Cells.EMPTY).test(none));
        assertFalse(ColumnFilter.NONE.withCells(Cells.EMPTY).test(ann));
        assertTrue(ColumnFilter.NONE.withCells(Cells.NON_EMPTY).test(ann));
        assertFalse(ColumnFilter.NONE.withText("ann").withNegate(true).test(ann));
        assertTrue(ColumnFilter.NONE.withText("bob").withNegate(true).test(ann));
        ColumnFilter invalid = ColumnFilter.NONE.withText("(").withMatch(Match.REGEX);
        assertTrue(invalid.error().isPresent());
        assertFalse(invalid.isActive());
        assertTrue(invalid.test(ann), "an invalid expression filters nothing");
        assertTrue(ColumnFilter.NONE.withNegate(true).hasOptions());
    }

    @Test
    void filtersCombine() {
        List<MetadataRow> rows = index(true).all();
        MetadataFilters f = MetadataFilters.NONE.withColumn("speaker", ColumnFilter.NONE.withCells(Cells.NON_EMPTY));
        assertEquals(2, filter(rows, f).size());
        assertEquals(1, filter(rows, f.withColumn("speaker", ColumnFilter.NONE.withText("bob"))).size());
        assertEquals(3, filter(rows, MetadataFilters.NONE.withShowWithoutMetadata(false)).size());
        assertEquals(2, filter(rows, MetadataFilters.NONE.withType(Optional.of("Header"))).size());
        assertEquals(List.of("1"), filter(rows, MetadataFilters.NONE.withSearch(ColumnFilter.NONE.withText("b.wav")))
            .stream().map(MetadataRow::label).toList());
        assertEquals(1, filter(rows, MetadataFilters.NONE.withColumn(MetadataFilters.FILE, ColumnFilter.NONE.withText("b.md"))
            .withColumn(MetadataFilters.OBJECT, ColumnFilter.NONE.withText("Only").withNegate(true))).size());
        assertFalse(f.cleared().isActive());
    }

    private static List<MetadataRow> filter(List<MetadataRow> rows, MetadataFilters f) {
        MetadataColumns columns = MetadataColumns.of(rows);
        return rows.stream().filter(f.predicate(r -> r.text().displayName(), MetadataRow::value, columns.keys())).toList();
    }

    @Test
    void cellEdits() {
        List<MetadataRow> rows = index(true).all();
        MetadataRow header = rows.get(0);
        MetadataRow session2 = rows.get(3);
        MetadataRow candidate = rows.get(4);
        assertInstanceOf(CellEdit.Set.class, CellEdit.of(header, "speaker", "Carl", false));
        assertEquals("Set speaker of the header", CellEdit.of(header, "speaker", "Carl", false).description());
        assertInstanceOf(CellEdit.None.class, CellEdit.of(header, "speaker", " Ann ", false));
        assertInstanceOf(CellEdit.Remove.class, CellEdit.of(header, "speaker", "", false));
        assertInstanceOf(CellEdit.Set.class, CellEdit.of(header, "speaker", "", true), "Set empty value");
        assertInstanceOf(CellEdit.Add.class, CellEdit.of(header, "place", "here", false));
        assertInstanceOf(CellEdit.None.class, CellEdit.of(header, "place", "  ", false));
        assertEquals("Add date to Section “Session 2”", CellEdit.of(session2, "date", "x", false).description());
        assertInstanceOf(CellEdit.AddHeader.class, CellEdit.of(candidate, "speaker", "Ann", false));
    }

    @Test
    void duplicatesAndValuesWithoutSpanAreReadOnly() {
        TextCoordinate span = new TextCoordinate(A, 0, 40, 1, 1);
        SimpleDependencyTree sentence = new SimpleDependencyTree("conll-grammar:sentence", span, "s1", Map.of(),
            List.of(), List.of(), List.of(), List.of(
                new Field("comment:sent_id", "s1", new TextCoordinate(A, 12, 13, 1, 1)),
                new Field("comment:note", "x", new TextCoordinate(A, 24, 24, 2, 2)),
                new Field("comment:note", "y", new TextCoordinate(A, 34, 34, 3, 3))));
        MetadataRow row = new MetadataRow(A, Optional.of(sentence), MetadataTypes.of(sentence), "s1", 1, 0);
        assertEquals("Sentence", row.type());
        assertInstanceOf(CellEdit.ReadOnly.class, CellEdit.of(row, "note", "z", false));
        assertInstanceOf(CellEdit.Set.class, CellEdit.of(row, "sent_id", "s2", false));
        SimpleDependencyTree withoutSpans = new SimpleDependencyTree("conll-grammar:sentence", span, "s1",
            Map.of("note", "x"), List.of(), List.of());
        MetadataRow plain = new MetadataRow(A, Optional.of(withoutSpans), "Sentence", "s1", 1, 0);
        assertInstanceOf(CellEdit.ReadOnly.class, CellEdit.of(plain, "note", "z", false));
    }

    @Test
    void naturalOrder() {
        List<String> values = new ArrayList<>(List.of("10", "2", "b", "B", "a2", "a10", "2024-05-12", "2024-5-3", "02"));
        values.sort(NaturalOrder.INSTANCE);
        assertEquals(List.of("2", "02", "10", "2024-5-3", "2024-05-12", "a2", "a10", "B", "b"), values);
    }
}
