package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleExample;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorpusTest {

    private static final TextId A = new TextId(Path.of("a.txt"));
    private static final TextId B = new TextId(Path.of("b.txt"));
    //                                    0         1         2
    //                                    0123456789012345678901234567
    private static final String TEXT_A = "<mami> {pig «a / b»}\n\nmore";

    private Corpus corpus;
    private SimpleLexicalDefinition entry;
    private SimpleSense sense;
    private SimpleExample example;

    static TextCoordinate span(TextId text, int start, int stop) {
        return new TextCoordinate(text, start, stop, 1, 1);
    }

    @BeforeEach
    void setUp() {
        corpus = new Corpus();
        example = new SimpleExample("g:example", span(A, 12, 18), "a", "b");
        sense = new SimpleSense("g:sense", span(A, 7, 19), "pig", List.of(example));
        entry = new SimpleLexicalDefinition("g:lexical-entry", span(A, 0, 19), "mami", List.of(sense));
        corpus.put(Text.builder(A, TEXT_A).add("g", List.of(entry), List.of()).build());
    }

    private Text a() {
        return corpus.text(A).orElseThrow();
    }

    @Test
    void objectAtReturnsDeepestObject() {
        assertSame(example, a().objectAt(12).orElseThrow());
        assertSame(example, a().objectAt(18).orElseThrow());
        assertSame(sense, a().objectAt(19).orElseThrow());
        assertSame(sense, a().objectAt(7).orElseThrow());
        assertSame(entry, a().objectAt(0).orElseThrow());
        assertTrue(a().objectAt(20).isEmpty());
        assertTrue(corpus.text(B).isEmpty());
    }

    @Test
    void queriesIncludeNestedObjects() {
        assertEquals(List.of(entry, sense, example), a().flatten().toList());
        assertEquals(List.of(sense), a().objectsOf(Sense.class));
        assertEquals(List.of(entry), corpus.objectsOf(LexicalEntry.class).toList());
        assertEquals(List.of(entry), a().objects());
        assertEquals("g", a().grammarIdOf(example).orElseThrow());
        assertSame(sense, a().parentOf(example).orElseThrow());
        assertTrue(a().parentOf(entry).isEmpty());
    }

    @Test
    void textOfFindsTheTextAndRejectsStaleObjects() {
        assertSame(a(), corpus.textOf(example).orElseThrow());
        corpus.put(Text.builder(A, TEXT_A).add("g", List.of(), List.of()).build());
        assertTrue(corpus.textOf(example).isEmpty(), "object of an outdated parse");
    }

    @Test
    void reparsingReplacesOnlyThatText() {
        SimpleIGT igtB = new SimpleIGT("g:igt", span(B, 0, 2), "t", "m", "f");
        corpus.put(Text.builder(B, "abc").add("g", List.of(igtB), List.of()).build());
        assertEquals(2, corpus.texts().size());

        Diagnostic error = Diagnostic.at(A, "g", 1, 0, "oops");
        corpus.put(Text.builder(A, TEXT_A).add("g", List.of(), List.of(error)).build());
        assertEquals(List.of(igtB), corpus.objectsOf(SimpleIGT.class).toList());
        assertEquals(List.of(error), a().diagnostics());

        corpus.put(Text.builder(A, TEXT_A).add("g", List.of(), List.of()).build());
        assertTrue(a().diagnostics().isEmpty(), "diagnostics of a text are replaced, not accumulated");

        corpus.remove(B);
        assertEquals(List.of(A), corpus.texts().stream().map(Text::id).toList());
    }

    @Test
    void grammarsKeepTheirOrder() {
        SimpleIGT first = new SimpleIGT("g2:igt", span(A, 22, 25), "t", "m", "f");
        Text text = Text.builder(A, TEXT_A)
            .add("g2", List.of(first), List.of())
            .add("g", List.of(entry), List.of())
            .build();
        assertEquals(List.of(first, entry), text.objects());
        assertEquals(List.of("g2", "g"), text.grammarIds());
    }

    @Test
    void invalidSpansAreRejectedAndReported() {
        SimpleIGT beyond = new SimpleIGT("g:igt", span(A, 20, 99), "t", "m", "f");
        SimpleIGT otherText = new SimpleIGT("g:igt", span(B, 0, 1), "t", "m", "f");
        SimpleExample outside = new SimpleExample("g:example", span(A, 22, 23), "a", "b");
        SimpleSense badParent = new SimpleSense("g:sense", span(A, 7, 19), "pig", List.of(outside));
        SimpleIGT empty = new SimpleIGT("g:igt", span(A, 5, 4), "t", "m", "f");
        SimpleIGT ok = new SimpleIGT("g:igt", span(A, 22, 25), "t", "m", "f");
        Text text = Text.builder(A, TEXT_A)
            .add("g", List.of(beyond, otherText, badParent, empty, ok), List.of())
            .build();

        assertEquals(List.of(ok), text.objects());
        assertEquals(4, text.diagnostics().size());
        assertTrue(text.diagnostics().get(0).message().contains("invalid span"));
    }

    @Test
    void fieldSpansAreValidated() {
        Field good = new Field(SimpleIGT.TRANSCRIPTION, "more", span(A, 22, 25));
        Field emptyAtEnd = new Field(SimpleIGT.MORPHEMES, "", span(A, 26, 25));
        Field outside = new Field(SimpleIGT.FREE_TRANSLATION, "x", span(A, 0, 1));
        SimpleIGT ok = new SimpleIGT("g:igt", span(A, 22, 25), "more", "", "", List.of(good, emptyAtEnd));
        SimpleIGT bad = new SimpleIGT("g:igt", span(A, 22, 25), "more", "", "x", List.of(outside));
        Text text = Text.builder(A, TEXT_A).add("g", List.of(ok, bad), List.of()).build();
        assertEquals(List.of(ok), text.objects());
        assertTrue(text.diagnostics().get(0).message().contains("field"));
    }

    @Test
    void listenersReceiveBeforeAndAfter() {
        List<String> events = new ArrayList<>();
        corpus.addListener((id, before, after) ->
            events.add(id.displayName() + ":" + before.isPresent() + ">" + after.isPresent()));
        corpus.put(Text.builder(B, "").build());
        corpus.put(Text.builder(B, "").build());
        corpus.remove(A);
        corpus.remove(A);
        assertEquals(List.of("b.txt:false>true", "b.txt:true>true", "a.txt:true>false"), events);
    }

    @Test
    void selfAndDescendantsIsDepthFirst() {
        assertEquals(List.of(entry, sense, example), Corpus.selfAndDescendants(entry).toList());
    }

    @Test
    void textIdOfNormalisesPaths() {
        assertEquals(TextId.of(Path.of("x/../a.txt")), TextId.of(Path.of("a.txt")));
        assertEquals("a.txt", TextId.of(Path.of("dir/a.txt")).displayName());
    }

    @Test
    void diagnosticSpan() {
        assertEquals(Optional.of(span(A, 2, 3)), new Diagnostic(A, "g", 1, 2, "m", 2, 3, List.of()).span());
        assertTrue(new Diagnostic(A, "g", 1, 2, "m", 4, 3, List.of()).span().isEmpty());
        assertTrue(Diagnostic.at(A, "g", 1, 2, "m").span().isEmpty());
    }
}
