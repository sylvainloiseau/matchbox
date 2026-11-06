package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rule of {@link MatchRole}, applied by {@link Text.Builder}. */
class OverlapResolverTest {

    private static final TextId A = new TextId(Path.of("a.md"));
    private static final String CONTENT = "x".repeat(100);

    private static TextCoordinate span(int start, int stop) {
        return new TextCoordinate(A, start, stop, 1 + start / 10, 1 + stop / 10);
    }

    private static SimpleIGT igt(String grammar, int start, int stop) {
        return new SimpleIGT(grammar + ":igt", span(start, stop), "t", "m", "f");
    }

    private static SimpleSection section(String grammar, int start, int stop, CorpusObject... content) {
        return new SimpleSection(grammar + ":section", span(start, stop), "T", Map.of(), List.of(content));
    }

    private static SimpleTag tag(String grammar, int start, int stop) {
        return new SimpleTag(grammar + ":tag", span(start, stop), "k");
    }

    private static Text.Builder builder() {
        return Text.builder(A, CONTENT);
    }

    @Test
    void roles_by_capability() {
        assertEquals(MatchRole.EXCLUSIVE, igt("a", 0, 1).getMatchRole());
        assertEquals(MatchRole.SCOPE, section("a", 0, 1).getMatchRole());
        assertEquals(MatchRole.ANNOTATION, tag("a", 0, 1).getMatchRole());
    }

    @Test
    void same_span_in_two_grammars_keeps_the_first() {
        SimpleIGT first = igt("a", 10, 19);
        SimpleIGT second = igt("b", 10, 19);
        Text text = builder()
            .add("a", "Grammar A", List.of(first), List.of())
            .add("b", "Grammar B", List.of(second), List.of())
            .build();
        assertEquals(List.of(first), text.objects());
        assertTrue(text.diagnostics("a").isEmpty());
        List<Diagnostic> warnings = text.diagnostics("b");
        assertEquals(1, warnings.size());
        Diagnostic w = warnings.get(0);
        assertTrue(w.isWarning());
        assertEquals(10, w.startIndex());
        assertEquals(19, w.stopIndex());
        assertEquals(2, w.line());
        assertTrue(w.message().contains("Grammar A"), w.message());
        assertTrue(w.message().startsWith("Not kept: line 2 already read as igt (line 2)"), w.message());
        assertEquals(1, text.conflicts().size());
        MatchConflict c = text.conflicts().get(0);
        assertEquals(MatchConflict.Kind.DROPPED, c.kind());
        assertTrue(c.isDropped());
        assertSame(second, c.object());
        assertSame(first, c.other());
        assertEquals("b", c.grammarId());
        assertEquals("a", c.otherGrammarId());
        assertEquals(A, c.text());
    }

    @Test
    void inclusion_and_crossing_are_dropped_both_ways() {
        // later inside earlier
        assertDropped(igt("a", 10, 29), igt("b", 15, 20));
        // earlier inside later
        assertDropped(igt("a", 15, 20), igt("b", 10, 29));
        // crossing
        assertDropped(igt("a", 10, 20), igt("b", 20, 30));
        assertDropped(igt("a", 20, 30), igt("b", 10, 20));
    }

    private static void assertDropped(SimpleIGT first, SimpleIGT second) {
        Text text = builder().add("a", List.of(first), List.of()).add("b", List.of(second), List.of()).build();
        assertEquals(List.of(first), text.objects());
        assertEquals(1, text.diagnostics("b").size());
    }

    @Test
    void the_order_of_the_grammars_decides() {
        SimpleIGT a = igt("a", 10, 19);
        SimpleIGT b = igt("b", 12, 25);
        Text text = builder().add("b", List.of(b), List.of()).add("a", List.of(a), List.of()).build();
        assertEquals(List.of(b), text.objects());
        assertSame(a, text.conflicts().get(0).object());
    }

    @Test
    void adjacent_objects_do_not_conflict() {
        Text text = builder()
            .add("a", List.of(igt("a", 10, 19)), List.of())
            .add("b", List.of(igt("b", 20, 29), igt("b", 0, 9)), List.of())
            .build();
        assertEquals(3, text.objects().size());
        assertTrue(text.conflicts().isEmpty());
        assertTrue(text.diagnostics().isEmpty());
    }

    @Test
    void a_scope_enclosing_objects_of_other_grammars_is_no_conflict() {
        Text text = builder()
            .add("n", List.of(igt("n", 10, 19), igt("n", 30, 39)), List.of())
            .add("m", List.of(section("m", 0, 49)), List.of())
            .build();
        assertEquals(3, text.objects().size());
        assertTrue(text.conflicts().isEmpty());
        // and in the other order
        text = builder()
            .add("m", List.of(section("m", 0, 49)), List.of())
            .add("n", List.of(igt("n", 10, 19), igt("n", 30, 39)), List.of())
            .build();
        assertEquals(3, text.objects().size());
        assertTrue(text.conflicts().isEmpty());
    }

    @Test
    void a_scope_crossing_an_exclusive_object_is_kept_and_reported() {
        SimpleSection s = section("m", 15, 49);
        Text text = builder()
            .add("n", List.of(igt("n", 10, 19)), List.of())
            .add("m", List.of(s), List.of())
            .build();
        assertEquals(2, text.objects().size());
        assertEquals(MatchConflict.Kind.SCOPE_CROSSING, text.conflicts().get(0).kind());
        assertSame(s, text.conflicts().get(0).object());
        assertTrue(text.diagnostics("m").get(0).isWarning());

        // the exclusive object of the later grammar crossing an earlier scope is the one reported
        SimpleIGT late = igt("n", 10, 19);
        text = builder()
            .add("m", List.of(s), List.of())
            .add("n", List.of(late), List.of())
            .build();
        assertEquals(2, text.objects().size());
        assertEquals(MatchConflict.Kind.SCOPE_CROSSING, text.conflicts().get(0).kind());
        assertSame(late, text.conflicts().get(0).object());
        assertEquals(1, text.diagnostics("n").size());
    }

    @Test
    void a_subsection_crossing_is_reported() {
        SimpleSection sub = section("m", 15, 29);
        Text text = builder()
            .add("n", List.of(igt("n", 10, 19)), List.of())
            .add("m", List.of(section("m", 0, 49, sub)), List.of())
            .build();
        assertEquals(1, text.conflicts().size());
        assertSame(sub, text.conflicts().get(0).object());
    }

    @Test
    void annotations_never_conflict() {
        Text text = builder()
            .add("n", List.of(igt("n", 10, 19)), List.of())
            .add("t", List.of(tag("t", 12, 14), tag("t", 18, 22)), List.of())
            .build();
        assertEquals(3, text.objects().size());
        assertTrue(text.conflicts().isEmpty());
        // an annotation does not block a later exclusive object
        text = builder()
            .add("t", List.of(tag("t", 12, 14)), List.of())
            .add("n", List.of(igt("n", 10, 19)), List.of())
            .build();
        assertEquals(2, text.objects().size());
    }

    @Test
    void an_exclusive_child_of_a_scope_meeting_an_earlier_claim_is_kept_and_reported() {
        SimpleTextHeader header = new SimpleTextHeader("m:header", span(10, 15), Map.of());
        SimpleSection s = section("m", 0, 49, header);
        Text text = builder()
            .add("n", List.of(igt("n", 10, 19)), List.of())
            .add("m", List.of(s), List.of())
            .build();
        assertEquals(2, text.objects().size());
        assertEquals(1, text.conflicts().size());
        assertEquals(MatchConflict.Kind.NESTED_OVERLAP, text.conflicts().get(0).kind());
        assertSame(header, text.conflicts().get(0).object());
    }

    @Test
    void the_exclusive_child_of_a_kept_scope_claims_its_characters() {
        SimpleTextHeader header = new SimpleTextHeader("m:header", span(10, 15), Map.of());
        Text text = builder()
            .add("m", List.of(section("m", 0, 49, header)), List.of())
            .add("n", List.of(igt("n", 12, 19)), List.of())
            .build();
        assertEquals(1, text.objects().size());
        assertTrue(text.conflicts().get(0).isDropped());
        assertSame(header, text.conflicts().get(0).other());
    }

    @Test
    void overlapping_objects_of_one_grammar_are_kept_and_reported() {
        SimpleIGT first = igt("n", 10, 19);
        SimpleIGT second = igt("n", 15, 25);
        Text text = builder().add("n", List.of(first, second), List.of()).build();
        assertEquals(2, text.objects().size());
        assertEquals(1, text.conflicts().size());
        MatchConflict c = text.conflicts().get(0);
        assertEquals(MatchConflict.Kind.SAME_GRAMMAR, c.kind());
        assertSame(second, c.object());
        assertSame(first, c.other());
        assertFalse(c.isDropped());
    }

    @Test
    void the_first_earlier_grammar_is_named() {
        Text text = builder()
            .add("a", List.of(igt("a", 20, 29)), List.of())
            .add("b", List.of(igt("b", 10, 15)), List.of())
            .add("c", List.of(igt("c", 10, 25)), List.of())
            .build();
        MatchConflict c = text.conflicts().get(0);
        assertEquals("a", c.otherGrammarId());
    }

    @Test
    void claims_of_the_grammars_added_so_far() {
        Text.Builder b = builder();
        assertTrue(b.claims().isEmpty());
        b.add("t", List.of(tag("t", 1, 2)), List.of());
        b.add("m", List.of(section("m", 0, 99)), List.of());
        assertTrue(b.claims().isEmpty());
        b.add("n", List.of(igt("n", 30, 39), igt("n", 10, 19)), List.of());
        b.add("x", List.of(igt("x", 15, 16)), List.of());
        List<Claim> claims = b.claims();
        assertEquals(2, claims.size());
        assertEquals(10, claims.get(0).span().startIndex());
        assertEquals("n", claims.get(0).grammarId());
        assertEquals("n:igt", claims.get(0).kind());
        assertTrue(claims.get(0).meets(19, 25));
        assertFalse(claims.get(0).meets(20, 25));
    }

    @Test
    void conflicts_of_the_corpus() {
        Corpus corpus = new Corpus();
        corpus.put(builder().add("a", List.of(igt("a", 1, 5)), List.of()).add("b", List.of(igt("b", 1, 5)), List.of()).build());
        assertEquals(1, corpus.conflicts().count());
    }

    @Test
    void severity_defaults_to_error() {
        assertTrue(Diagnostic.at(A, "g", 1, 0, "m").isError());
        assertTrue(new Diagnostic(A, "g", 1, 0, "m", 0, 1, List.of()).isError());
        assertEquals(Diagnostic.Severity.ERROR,
            new Diagnostic(A, "g", 1, 0, "m", 0, 1, List.of(), null).severity());
        assertTrue(Diagnostic.warning(A, "g", 1, 0, "m", 0, 1).isWarning());
    }
}
