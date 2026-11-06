package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.simple.SimpleExample;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Drawing order ({@link Text#layered()}) and what a click selects ({@link Text#objectAt}). */
class MatchLayeringTest {

    private static final TextId A = new TextId(Path.of("a.md"));
    private static final String CONTENT = "x".repeat(100);

    private static TextCoordinate span(int start, int stop) {
        return new TextCoordinate(A, start, stop, 1, 1);
    }

    private final SimpleSection sub = new SimpleSection("m:section", span(40, 99), "Sub", Map.of(), List.of());
    private final SimpleSection section = new SimpleSection("m:section", span(0, 99), "Top", Map.of(), List.of(sub));
    private final SimpleIGT igt = new SimpleIGT("n:igt", span(10, 19), "t", "m", "f");
    private final SimpleExample example = new SimpleExample("n:example", span(55, 58), "a", "b");
    private final SimpleSense sense = new SimpleSense("n:sense", span(52, 59), "pig", List.of(example));
    private final SimpleLexicalDefinition entry = new SimpleLexicalDefinition("n:entry", span(50, 59), "mami", List.of(sense));
    private final SimpleTag tag = new SimpleTag("t:tag", span(12, 14), "k");

    private Text text(boolean markdownFirst) {
        Text.Builder b = Text.builder(A, CONTENT);
        if (markdownFirst) b.add("m", List.of(section), List.of());
        b.add("t", List.of(tag), List.of());
        b.add("n", List.of(igt, entry), List.of());
        if (!markdownFirst) b.add("m", List.of(section), List.of());
        return b.build();
    }

    @Test
    void scopes_then_exclusive_objects_then_annotations() {
        for (boolean markdownFirst : new boolean[] {true, false}) {
            assertEquals(List.of(section, sub, igt, entry, sense, example, tag), text(markdownFirst).layered());
        }
    }

    @Test
    void a_click_selects_what_is_drawn_on_top() {
        for (boolean markdownFirst : new boolean[] {true, false}) {
            Text t = text(markdownFirst);
            assertSame(igt, t.objectAt(10).orElseThrow(), "in the IGT");
            assertSame(tag, t.objectAt(13).orElseThrow(), "on the tag");
            assertSame(section, t.objectAt(25).orElseThrow(), "prose of the section");
            assertSame(sub, t.objectAt(45).orElseThrow(), "prose of the subsection");
            assertSame(example, t.objectAt(56).orElseThrow(), "deepest exclusive object");
            assertSame(sense, t.objectAt(53).orElseThrow());
            assertSame(entry, t.objectAt(50).orElseThrow());
        }
    }

    @Test
    void nothing_outside_the_objects() {
        Text t = Text.builder(A, CONTENT).add("n", List.of(igt), List.of()).build();
        assertTrue(t.objectAt(5).isEmpty());
        assertTrue(t.objectAt(20).isEmpty());
    }
}
