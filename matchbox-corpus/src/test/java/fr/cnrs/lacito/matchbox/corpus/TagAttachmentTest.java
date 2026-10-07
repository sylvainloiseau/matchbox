package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TagAttachmentTest {

    private static final TextId T = new TextId(Path.of("t.md"));

    private static final String CONTENT = String.join("\n",
        "#\"whole text\"",              // 1  outside everything
        "",                             // 2
        "# Section",                    // 3  section: lines 3-20
        "#\"after heading\"",           // 4  below line 3? no object starts/ends on 3 except the section (contains it)
        "",                             // 5
        ":::",                          // 6  IGT: lines 6-10
        "a mio",                        // 7
        "1sg go",                       // 8
        "'I went' #\"inside igt\"",     // 9
        ":::",                          // 10
        "#\"below igt\"",               // 11
        "#\"below igt too\"",           // 12
        "",                             // 13
        "#\"above entry\"",             // 14
        "<mami> {pig #\"in sense\"}",   // 15 entry and sense
        "free text #\"in free text\"",  // 16 next to the entry above
        "<jaflo> {canoe}",              // 17 entry
        "",                             // 18
        "#\"alone in section\"",        // 19
        "end of section");              // 20

    private int at(String s) {
        int i = CONTENT.indexOf(s);
        if (i < 0) throw new AssertionError(s);
        return i;
    }

    private TextCoordinate span(int start, int stop) {
        return new TextCoordinate(T, start, stop, 1, 1);
    }

    private final List<SimpleTag> tags = new ArrayList<>();

    private SimpleTag tag(String name) {
        int start = at("#\"" + name + "\"");
        SimpleTag t = new SimpleTag("t:tag", span(start, start + name.length() + 2), name);
        tags.add(t);
        return t;
    }

    @Test
    void targets() {
        SimpleSection section = new SimpleSection("s:section", span(at("# Section"), CONTENT.length() - 1), "Section",
            Map.of(), List.of());
        SimpleIGT igt = new SimpleIGT("n:igt", span(at(":::"), at("#\"below igt\"") - 1), "a mio", "1sg go", "'I went'");
        int mami = at("<mami>");
        int mamiEnd = CONTENT.indexOf('\n', mami) - 1;
        SimpleSense sense = new SimpleSense("n:sense", span(at("{pig"), mamiEnd), "pig", List.of());
        SimpleLexicalDefinition entry = new SimpleLexicalDefinition("n:entry", span(mami, mamiEnd), "mami", List.of(sense));
        int jaflo = at("<jaflo>");
        SimpleLexicalDefinition entry2 = new SimpleLexicalDefinition("n:entry",
            span(jaflo, CONTENT.indexOf('\n', jaflo) - 1), "jaflo", List.of());

        SimpleTag whole = tag("whole text");
        SimpleTag afterHeading = tag("after heading");
        SimpleTag insideIgt = tag("inside igt");
        SimpleTag belowIgt = tag("below igt");
        SimpleTag belowIgtToo = tag("below igt too");
        SimpleTag aboveEntry = tag("above entry");
        SimpleTag inSense = tag("in sense");
        SimpleTag inFreeText = tag("in free text");
        SimpleTag alone = tag("alone in section");

        Text text = Text.builder(T, CONTENT)
            .add("s", List.of(section), List.of())
            .add("n", List.of(igt, entry, entry2), List.of())
            .add("t", List.copyOf(tags), List.of())
            .build();
        assertTrue(text.diagnostics().isEmpty(), () -> text.diagnostics().toString());

        assertEquals(Optional.empty(), text.targetOf(whole));
        assertSame(section, text.targetOf(afterHeading).orElseThrow(), "the heading line is the section itself");
        assertSame(igt, text.targetOf(insideIgt).orElseThrow());
        assertSame(igt, text.targetOf(belowIgt).orElseThrow(), "just below the IGT");
        assertSame(igt, text.targetOf(belowIgtToo).orElseThrow(), "after another tag line");
        assertSame(entry, text.targetOf(aboveEntry).orElseThrow(), "the entry, not its sense");
        assertSame(sense, text.targetOf(inSense).orElseThrow(), "contained: the innermost object");
        assertSame(entry, text.targetOf(inFreeText).orElseThrow(), "between two entries: the one above");
        assertSame(section, text.targetOf(alone).orElseThrow(), "separated by blank lines");

        assertEquals(List.of(insideIgt, belowIgt, belowIgtToo), text.tagsOf(igt));
        assertEquals(List.of(whole), text.textTags());
        assertTrue(text.tagsOf(entry2).isEmpty());
    }

    @Test
    void paths() {
        Tag tag = new SimpleTag("t:tag", span(0, 3), " verb / / infinitive ");
        assertEquals("verb / / infinitive", tag.getName());
        assertEquals(List.of("verb", "infinitive"), tag.getPath());
        assertEquals("infinitive", tag.getLeaf());
        assertEquals("x", new SimpleTag("t:tag", span(0, 3), "x").getLeaf());
    }
}
