package fr.cnrs.lacito.matchbox.gui.problems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link ProblemIndex}, {@link ProblemRow}, {@link ProblemNavigator}, {@link ProblemExport}, {@link ProblemFixes}. */
class ProblemModelTest {

    // a.txt sorts before B.TXT (FileOrder: case ignored first), on every system
    private static final TextId A = new TextId(Path.of("proj", "a.txt"));
    private static final TextId B = new TextId(Path.of("proj", "B.TXT"));
    private static final String CONTENT = "line one\nline two\nline three\n";

    private static Diagnostic semantic(TextId t, int line, String message) {
        int start = CONTENT.indexOf("line", 0);
        for (int l = 1; l < line; l++) start = CONTENT.indexOf('\n', start) + 1;
        return Diagnostic.semantic(t, "g", line, 0, message, start, start + 3, Diagnostic.Severity.ERROR);
    }

    private static Text text(TextId t, Diagnostic... diagnostics) {
        return Text.builder(t, CONTENT).add("g", List.of(), List.of(diagnostics)).build();
    }

    @Test
    void index_replaces_the_rows_of_one_text_and_keeps_project_order() {
        ProblemIndex index = new ProblemIndex();
        List<TextId> changes = new ArrayList<>();
        index.addListener(changes::add);
        index.update(B, Optional.of(text(B, semantic(B, 1, "b1"))));
        index.update(A, Optional.of(text(A, semantic(A, 3, "a3"), semantic(A, 1, "a1"))));
        assertEquals(List.of("a1", "a3", "b1"), index.rows().stream().map(r -> r.diagnostic().message()).toList());
        assertEquals(List.of(B, A), changes);

        index.update(A, Optional.of(text(A)));
        assertEquals(List.of("b1"), index.rows().stream().map(r -> r.diagnostic().message()).toList());
        index.update(B, Optional.empty());
        assertTrue(index.rows().isEmpty());
        // nothing before, nothing after: no event
        index.update(A, Optional.of(text(A)));
        assertEquals(4, changes.size());
    }

    @Test
    void next_across_the_project_wraps_and_skips_the_current_line() {
        ProblemIndex index = new ProblemIndex();
        index.update(A, Optional.of(text(A, semantic(A, 1, "a1"), semantic(A, 3, "a3"))));
        index.update(B, Optional.of(text(B, semantic(B, 2, "b2"))));
        assertEquals("a3", index.next(A, 1, true, r -> true).orElseThrow().diagnostic().message());
        assertEquals("b2", index.next(A, 3, true, r -> true).orElseThrow().diagnostic().message());
        assertEquals("a1", index.next(B, 2, true, r -> true).orElseThrow().diagnostic().message());
        assertEquals("a3", index.next(B, 2, false, r -> true).orElseThrow().diagnostic().message());
        assertEquals("b2", index.next(A, 1, false, r -> true).orElseThrow().diagnostic().message());
        assertEquals("a1", index.next(null, 0, true, r -> true).orElseThrow().diagnostic().message());
        assertTrue(index.next(A, 1, true, r -> false).isEmpty());
    }

    @Test
    void row_object_is_the_innermost_object_of_the_grammar() {
        SimpleTag tag = new SimpleTag("g:tag", new TextCoordinate(A, 9, 16, 2, 2), "two");
        Diagnostic d = Diagnostic.semantic(A, "g", 2, 0, "m", 10, 12, Diagnostic.Severity.ERROR);
        Diagnostic other = Diagnostic.semantic(A, "h", 2, 0, "m", 10, 12, Diagnostic.Severity.ERROR);
        Text t = Text.builder(A, CONTENT).add("g", List.of(tag), List.of(d)).add("h", List.of(), List.of(other)).build();
        List<ProblemRow> rows = ProblemRow.of(t);
        assertEquals("two", rows.get(0).objectLabel());
        assertEquals("—", rows.get(1).objectLabel(), "objects of another grammar are not used");
        assertEquals("a.txt", rows.get(0).fileName());
    }

    @Test
    void navigator_in_a_file() {
        List<Diagnostic> ds = List.of(semantic(A, 3, "a3"), semantic(A, 1, "a1"), semantic(A, 1, "a1bis"));
        assertEquals("a3", ProblemNavigator.next(ds, 1, true).orElseThrow().message());
        assertEquals("a1", ProblemNavigator.next(ds, 3, true).orElseThrow().message(), "wraps");
        assertEquals("a1", ProblemNavigator.next(ds, 3, false).orElseThrow().message(), "first of its line");
        assertEquals("a3", ProblemNavigator.next(ds, 1, false).orElseThrow().message(), "wraps");
        assertTrue(ProblemNavigator.next(List.of(), 1, true).isEmpty());
    }

    @Test
    void export_quotes_values_and_writes_relative_paths_with_slashes() {
        Diagnostic d = semantic(A, 2, "a \"quoted\", value").withCode("g.code");
        ProblemRow row = ProblemRow.of(text(A, d)).get(0);
        String csv = ProblemExport.csv(List.of(row), Optional.of(Path.of("proj")), id -> "Grammar " + id);
        assertEquals("File,Line,Position,Severity,Category,Plugin,Code,Object,Message\r\n"
            + "a.txt,2,0,Error,semantic,Grammar g,g.code,,\"a \"\"quoted\"\", value\"\r\n", csv);
        TextId nested = new TextId(Path.of("proj", "sub", "c.txt"));
        assertEquals("sub/c.txt", ProblemExport.relativePath(nested.path(), Optional.of(Path.of("proj"))));
        String tsv = ProblemExport.tsv(List.of(row), Optional.empty(), id -> id);
        assertTrue(tsv.startsWith("File\tLine\t"));
        assertEquals("a.txt:2: a \"quoted\", value", ProblemExport.line(row));
    }

    @Test
    void fixes_become_actions_on_the_line_of_their_problem() {
        Diagnostic.Fix fix = new Diagnostic.Fix("Delete", List.of(TextEdit.replace(CONTENT, 9, 13, "")));
        Diagnostic d = semantic(A, 2, "bad").withFixes(List.of(fix));
        List<TextAction> actions = ProblemFixes.actions(text(A, d, semantic(A, 3, "no fix")));
        assertEquals(1, actions.size());
        assertEquals("Fix: Delete", actions.get(0).label());
        assertEquals(9, actions.get(0).offset());
        assertEquals("bad", actions.get(0).description());
    }
}
