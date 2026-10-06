package fr.cnrs.lacito.matchbox.corpus;

import static fr.cnrs.lacito.matchbox.corpus.CorpusTest.span;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionTest {

    private static final TextId T = new TextId(Path.of("t.md"));
    private static final String CONTENT = "x".repeat(100);

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void simpleSectionKeepsOrderAndRejectsNulls() {
        SimpleSection s = new SimpleSection("s:section", span(T, 0, 9), null,
            map("z", "1", "a", "2", "m", "3"), List.of());
        assertEquals(List.of("z", "a", "m"), List.copyOf(s.getMetadata().keySet()));
        assertEquals("", s.getTitle());
        assertEquals("s:section", s.getLabel(), "untitled: the kind");
        Map<String, String> withNull = new HashMap<>();
        withNull.put("k", null);
        assertThrows(NullPointerException.class,
            () -> new SimpleSection("s:section", span(T, 0, 9), "t", withNull, List.of()));
    }

    @Test
    void subsectionsAreTheSectionsOfTheContent() {
        SimpleIGT igt = new SimpleIGT("s:igt", span(T, 2, 3), "a", "b", "c");
        SimpleSection sub = new SimpleSection("s:section", span(T, 4, 8), "Sub", Map.of(), List.of());
        SimpleSection s = new SimpleSection("s:section", span(T, 0, 9), "Top", Map.of(), List.of(igt, sub));
        assertEquals(List.of(sub), s.getSubsections());
        assertEquals(List.of(igt, sub), s.getChildren());
        assertEquals("Top", s.getLabel());
    }

    @Test
    void enclosingSectionsAndMetadataAcrossGrammars() {
        // grammar "s" gives the structure, grammar "g" the sentences
        SimpleSection inner = new SimpleSection("s:section", span(T, 20, 59), "Inner",
            map("speaker", "B", "date", "2024"), List.of());
        SimpleSection outer = new SimpleSection("s:section", span(T, 10, 89), "Outer",
            map("speaker", "A", "place", "Here"), List.of(inner));
        SimpleTextHeader header = new SimpleTextHeader("s:header", span(T, 0, 9),
            map("language", "Tuwari", "speaker", "Nobody"));
        SimpleIGT inInner = new SimpleIGT("g:igt", span(T, 30, 40), "a", "b", "c");
        SimpleIGT inOuter = new SimpleIGT("g:igt", span(T, 70, 80), "a", "b", "c");
        SimpleIGT outside = new SimpleIGT("g:igt", span(T, 92, 95), "a", "b", "c");
        Text text = Text.builder(T, CONTENT)
            .add("s", List.of(header, outer), List.of())
            .add("g", List.of(inInner, inOuter, outside), List.of())
            .build();

        assertEquals(List.<Section>of(outer, inner), text.sections());
        assertEquals(List.<Section>of(outer, inner), text.enclosingSections(inInner));
        assertEquals(List.<Section>of(outer), text.enclosingSections(inOuter));
        assertEquals(List.<Section>of(outer), text.enclosingSections(inner));
        assertTrue(text.enclosingSections(outside).isEmpty());

        assertEquals(map("language", "Tuwari", "speaker", "Nobody"), text.properties());
        assertEquals(map("language", "Tuwari", "speaker", "B", "place", "Here", "date", "2024"),
            text.metadataOf(inInner));
        assertEquals("A", text.metadataOf(inOuter).get("speaker"));
        assertEquals("B", text.metadataOf(inner).get("speaker"), "a section's own metadata wins");
        assertEquals("Nobody", text.metadataOf(outside).get("speaker"));
    }
}
