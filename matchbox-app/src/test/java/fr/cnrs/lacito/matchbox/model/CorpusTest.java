package fr.cnrs.lacito.matchbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleExample;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorpusTest {

    private static final Path A = Path.of("a.txt");
    private static final Path B = Path.of("b.txt");
    //                                    0         1         2
    //                                    0123456789012345678901234567
    private static final String TEXT_A = "<mami> {pig «a / b»}\n\nmore";

    private Corpus corpus;
    private SimpleLexicalDefinition entry;
    private SimpleSense sense;
    private SimpleExample example;

    private static TextCoordinate span(Path file, int start, int stop) {
        return new TextCoordinate(file, start, stop, 1, 1);
    }

    @BeforeEach
    void setUp() {
        corpus = new Corpus();
        example = new SimpleExample("g:example", span(A, 12, 18), "a", "b");
        sense = new SimpleSense("g:sense", span(A, 7, 19), "pig", List.of(example));
        entry = new SimpleLexicalDefinition("g:lexical-entry", span(A, 0, 19), "mami", List.of(sense));
        corpus.setResults(A, Map.of("g", new ParseResult(List.of(entry), List.of())), TEXT_A);
    }

    @Test
    void objectAtReturnsDeepestObject() {
        assertSame(example, corpus.objectAt(A, 12).orElseThrow());
        assertSame(example, corpus.objectAt(A, 18).orElseThrow());
        assertSame(sense, corpus.objectAt(A, 19).orElseThrow());
        assertSame(sense, corpus.objectAt(A, 7).orElseThrow());
        assertSame(entry, corpus.objectAt(A, 0).orElseThrow());
        assertTrue(corpus.objectAt(A, 20).isEmpty());
        assertTrue(corpus.objectAt(B, 0).isEmpty());
    }

    @Test
    void queriesIncludeNestedObjects() {
        assertEquals(List.of(entry, sense, example), corpus.flatten(A).toList());
        assertEquals(List.of(sense), corpus.objectsOf(Sense.class, A));
        assertEquals(List.of(entry), corpus.objectsOf(LexicalEntry.class));
        assertEquals(List.of(entry), corpus.objectsFor(A));
        assertEquals("g", corpus.grammarIdOf(A, example).orElseThrow());
    }

    @Test
    void reparsingReplacesOnlyThatFile() {
        SimpleIGT igtB = new SimpleIGT("g:igt", span(B, 0, 2), "t", "m", "f");
        corpus.setResults(B, Map.of("g", new ParseResult(List.of(igtB), List.of())), "abc");
        assertEquals(2, corpus.getCorpus().size());

        ParseError error = new ParseError(A, "g", 1, 0, "oops");
        corpus.setResults(A, Map.of("g", new ParseResult(List.of(), List.of(error))), TEXT_A);
        assertEquals(List.of(igtB), corpus.getCorpus());
        assertEquals(List.of(error), corpus.errorsFor(A));

        corpus.setResults(A, Map.of("g", new ParseResult(List.of(), List.of())), TEXT_A);
        assertTrue(corpus.getErrors().isEmpty(), "errors of a file are replaced, not accumulated");

        corpus.removeFile(B);
        assertTrue(corpus.getCorpus().isEmpty());
    }

    @Test
    void grammarsKeepTheirOrder() {
        SimpleIGT first = new SimpleIGT("g2:igt", span(A, 22, 25), "t", "m", "f");
        Map<String, ParseResult> results = new LinkedHashMap<>();
        results.put("g2", new ParseResult(List.of(first), List.of()));
        results.put("g", new ParseResult(List.of(entry), List.of()));
        corpus.setResults(A, results, TEXT_A);
        assertEquals(List.of(first, entry), corpus.objectsFor(A));
    }

    @Test
    void invalidSpansAreRejectedAndReported() {
        SimpleIGT beyond = new SimpleIGT("g:igt", span(A, 20, 99), "t", "m", "f");
        SimpleIGT otherFile = new SimpleIGT("g:igt", span(B, 0, 1), "t", "m", "f");
        SimpleExample outside = new SimpleExample("g:example", span(A, 22, 23), "a", "b");
        SimpleSense badParent = new SimpleSense("g:sense", span(A, 7, 19), "pig", List.of(outside));
        SimpleIGT ok = new SimpleIGT("g:igt", span(A, 22, 25), "t", "m", "f");
        corpus.setResults(A, Map.of("g", new ParseResult(List.of(beyond, otherFile, badParent, ok), List.of())), TEXT_A);

        assertEquals(List.of(ok), corpus.objectsFor(A));
        assertEquals(3, corpus.errorsFor(A).size());
        assertTrue(corpus.errorsFor(A).get(0).getMessage().contains("invalid span"));
    }

    @Test
    void listenersAreNotifiedPerFile() {
        List<Path> notified = new ArrayList<>();
        corpus.addFileListener(notified::add);
        corpus.setResults(B, Map.of(), "");
        corpus.removeFile(A);
        corpus.removeFile(A);
        assertEquals(List.of(B, A), notified);
    }

    @Test
    void selfAndDescendantsIsDepthFirst() {
        List<CorpusObject> all = Corpus.selfAndDescendants(entry).toList();
        assertEquals(List.of(entry, sense, example), all);
    }
}
