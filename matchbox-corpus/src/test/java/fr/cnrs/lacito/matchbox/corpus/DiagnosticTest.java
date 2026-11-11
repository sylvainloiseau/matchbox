package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Categories, codes and fixes of {@link Diagnostic}; the constructors kept for existing plugins. */
class DiagnosticTest {

    private static final TextId A = new TextId(Path.of("a.txt"));

    @Test
    void legacy_constructors_give_syntax_diagnostics_without_code_nor_fix() {
        Diagnostic eight = new Diagnostic(A, "g", 1, 2, "m", 3, 4, List.of("'<'"));
        Diagnostic at = Diagnostic.at(A, "g", 1, 0, "m");
        Diagnostic warning = Diagnostic.warning(A, "g", 1, 0, "m", 0, 1);
        for (Diagnostic d : List.of(eight, at, warning)) {
            assertEquals(Diagnostic.Category.SYNTAX, d.category());
            assertEquals("", d.code());
            assertTrue(d.fixes().isEmpty());
            assertFalse(d.isSemantic());
        }
        assertTrue(eight.isError());
        assertTrue(warning.isWarning());
    }

    @Test
    void semantic_and_internal_factories() {
        Diagnostic s = Diagnostic.semantic(A, "g", 3, 1, "bad range", 10, 15, Diagnostic.Severity.WARNING);
        assertTrue(s.isSemantic());
        assertTrue(s.isWarning());
        assertEquals(10, s.span().orElseThrow().startIndex());

        Diagnostic i = Diagnostic.internal(A, "g", 0, "boom");
        assertEquals(Diagnostic.Category.INTERNAL, i.category());
        assertTrue(i.isError());
        assertEquals("internal", i.code());
    }

    @Test
    void with_code_and_fixes_copy_the_rest() {
        Diagnostic.Fix fix = new Diagnostic.Fix("Swap", List.of(TextEdit.insert(0, "x")));
        Diagnostic d = Diagnostic.semantic(A, "g", 3, 1, "m", 10, 15, Diagnostic.Severity.ERROR)
            .withCode("g.code").withFixes(List.of(fix));
        assertEquals("g.code", d.code());
        assertEquals(List.of(fix), d.fixes());
        assertEquals("Swap", fix.description());
        assertTrue(d.isSemantic());
        assertEquals(3, d.line());
        assertEquals(Diagnostic.Category.OVERLAP, d.withCategory(Diagnostic.Category.OVERLAP).category());
    }
}
