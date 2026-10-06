package fr.cnrs.lacito.matchbox.gui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionViewFactoryTest {

    private static final TextId T = new TextId(Path.of("t.md"));

    private static TextCoordinate span(int start, int stop) {
        return new TextCoordinate(T, start, stop, 1, 1);
    }

    @Test
    void contentIncludesObjectsOfOtherGrammarsInTextOrder() {
        SimpleSection sub = new SimpleSection("s:section", span(40, 59), "Sub", Map.of(), List.of());
        SimpleSection top = new SimpleSection("s:section", span(0, 89), "Top", Map.of(), List.of(sub));
        SimpleIGT before = new SimpleIGT("g:igt", span(10, 20), "a", "b", "c");
        SimpleIGT inSub = new SimpleIGT("g:igt", span(45, 50), "a", "b", "c");
        SimpleIGT after = new SimpleIGT("g:igt", span(70, 80), "a", "b", "c");
        Text text = Text.builder(T, "x".repeat(100))
            .add("s", List.of(top), List.of())
            .add("g", List.of(before, inSub, after), List.of())
            .build();
        assertEquals(List.<CorpusObject>of(before, sub, after), SectionViewFactory.contentInText(text, top));
        assertEquals(List.<CorpusObject>of(inSub), SectionViewFactory.contentInText(text, sub));
        assertEquals("1 subsection · 2 objects (2 Igt)",
            SectionViewFactory.summary(SectionViewFactory.contentInText(text, top)));
    }
}
