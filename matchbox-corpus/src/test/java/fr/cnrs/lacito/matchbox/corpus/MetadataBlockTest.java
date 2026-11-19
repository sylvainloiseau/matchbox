package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.capability.HasMetadata;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleDependencyTree;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The metadata blocks the capabilities give by default, from their maps and fields. */
class MetadataBlockTest {

    private static final TextId ID = new TextId(Path.of("t.md"));

    private static TextCoordinate span(int start, int stop) {
        return new TextCoordinate(ID, start, stop, 1, 1);
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void aHeaderIsABlockWithTheSpansOfItsFields() {
        SimpleTextHeader header = new SimpleTextHeader("g:header", span(0, 30), map("speaker", "Ann", "date", "2024"),
            List.of(new Field(TextHeader.propertyField("speaker"), "Ann", span(13, 15))));
        HasMetadata m = header;
        MetadataBlock block = m.metadataBlock().orElseThrow();
        assertEquals(Optional.of(span(0, 30)), block.span());
        assertEquals(List.of("speaker", "date"), block.entries().stream().map(MetadataEntry::key).toList());
        assertEquals(Optional.of(span(13, 15)), block.entry("speaker").orElseThrow().valueSpan());
        assertEquals(Optional.empty(), block.entry("date").orElseThrow().valueSpan(), "no field: read-only");
        assertEquals("property:date", m.metadataFieldName("date"));
    }

    @Test
    void aSectionWithoutMetadataHasNoBlock() {
        SimpleSection none = new SimpleSection("g:section", span(0, 9), "A", Map.of(), List.of());
        assertTrue(none.metadataBlock().isEmpty());
        assertTrue(none.metadataValues().isEmpty());
        SimpleSection some = new SimpleSection("g:section", span(0, 9), "A", map("date", "x"), List.of());
        assertEquals(Map.of("date", "x"), some.metadataValues());
        assertEquals(Section.metadataField("date"), some.metadataFieldName("date"));
    }

    @Test
    void duplicatesAreKeptAndTheFirstValueWins() {
        MetadataBlock block = new MetadataBlock(Optional.empty(), List.of(
            new MetadataEntry("a", "1", Optional.empty()), new MetadataEntry("b", "2", Optional.empty()),
            new MetadataEntry("a", "3", Optional.empty())));
        assertTrue(block.hasDuplicate("a"));
        assertFalse(block.hasDuplicate("b"));
        assertEquals(map("a", "1", "b", "2"), block.values());
        assertTrue(block.entry("A").isEmpty(), "keys are matched exactly");
    }

    @Test
    void aDependencySentenceWithFieldsListsThemInTextOrder() {
        SimpleDependencyTree s = new SimpleDependencyTree("g:sentence", span(0, 50), "s1", map("text", "Hi"),
            List.of(), List.of(), List.of(), List.of(
                new Field("comment:sent_id", "s1", span(12, 13)), new Field("comment:text", "Hi", span(24, 25))));
        assertEquals(List.of("sent_id", "text"),
            s.metadataBlock().orElseThrow().entries().stream().map(MetadataEntry::key).toList());
        SimpleDependencyTree plain = new SimpleDependencyTree("g:sentence", span(0, 50), "s1", map("text", "Hi"),
            List.of(), List.of());
        assertEquals(Map.of("text", "Hi"), plain.metadataValues(), "without fields: the map");
    }
}
