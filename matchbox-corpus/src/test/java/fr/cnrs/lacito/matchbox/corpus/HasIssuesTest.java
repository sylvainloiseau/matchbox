package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link Text.Builder} turns the {@link HasIssues} of the kept objects into semantic diagnostics. */
class HasIssuesTest {

    private static final TextId A = new TextId(Path.of("a.txt"));
    // line 1: offsets 0-9, line 2: 11-20, line 3: 22-31
    private static final String CONTENT = "aaaaaaaaaa\nbbbbbbbbbb\ncccccccccc\n";

    private static TextCoordinate span(int start, int stop, int line) {
        return new TextCoordinate(A, start, stop, line, line);
    }

    /** An object with issues and children. */
    private static final class Checked extends AbstractCorpusObject implements HasIssues {
        private final List<Issue> issues;
        private final List<CorpusObject> children;

        Checked(String kind, TextCoordinate span, List<Issue> issues, CorpusObject... children) {
            super(kind, span);
            this.issues = issues;
            this.children = List.of(children);
        }

        @Override
        public List<Issue> issues() {
            return issues;
        }

        @Override
        public List<CorpusObject> getChildren() {
            return children;
        }
    }

    @Test
    void issues_of_objects_and_children_become_semantic_diagnostics_of_the_grammar() {
        Diagnostic.Fix fix = new Diagnostic.Fix("Delete", List.of(TextEdit.replace(CONTENT, 13, 15, "")));
        Checked child = new Checked("g:child", span(11, 20, 2),
            List.of(Issue.error("bad child", "g.child").at(span(13, 14, 2)).withFixes(List.of(fix))));
        Checked parent = new Checked("g:parent", span(0, 31, 1),
            List.of(Issue.warning("doubtful parent", "g.parent")), child);
        Text text = Text.builder(A, CONTENT).add("g", List.of(parent), List.of()).build();

        List<Diagnostic> semantic = text.diagnostics(Diagnostic.Category.SEMANTIC);
        assertEquals(2, semantic.size());
        Diagnostic onParent = semantic.get(0);
        assertEquals("doubtful parent", onParent.message());
        assertTrue(onParent.isWarning());
        assertEquals("g", onParent.grammarId());
        assertEquals(0, onParent.startIndex());
        assertEquals(31, onParent.stopIndex());
        assertEquals("g.parent", onParent.code());

        Diagnostic onChild = semantic.get(1);
        assertEquals("bad child", onChild.message());
        assertEquals(2, onChild.line());
        assertEquals(2, onChild.column());
        assertEquals(13, onChild.startIndex());
        assertEquals(List.of(fix), onChild.fixes());
        assertEquals(semantic, text.diagnostics("g"));
    }

    @Test
    void an_issue_outside_its_object_is_placed_on_the_object() {
        Checked o = new Checked("g:o", span(11, 20, 2), List.of(Issue.error("x", "").at(span(0, 3, 1))));
        Diagnostic d = Text.builder(A, CONTENT).add("g", List.of(o), List.of()).build().diagnostics().get(0);
        assertEquals(11, d.startIndex());
        assertEquals(20, d.stopIndex());
        assertEquals(2, d.line());
        assertEquals(0, d.column());
    }

    @Test
    void objects_not_kept_report_nothing() {
        Checked invalid = new Checked("g:o", span(0, 500, 1), List.of(Issue.error("never seen", "")));
        Text text = Text.builder(A, CONTENT).add("g", List.of(invalid), List.of()).build();
        assertTrue(text.diagnostics(Diagnostic.Category.SEMANTIC).isEmpty());
        assertEquals(Diagnostic.Category.INTERNAL, text.diagnostics().get(0).category());
        assertEquals("invalid-span", text.diagnostics().get(0).code());

        Checked first = new Checked("a:o", span(0, 9, 1), List.of());
        Checked dropped = new Checked("b:o", span(0, 9, 1), List.of(Issue.error("never seen", "")));
        Text overlapping = Text.builder(A, CONTENT)
            .add("a", List.of(first), List.of())
            .add("b", List.of(dropped), List.of())
            .build();
        assertTrue(overlapping.diagnostics(Diagnostic.Category.SEMANTIC).isEmpty());
        Diagnostic overlap = overlapping.diagnostics("b").get(0);
        assertEquals(Diagnostic.Category.OVERLAP, overlap.category());
        assertEquals("overlap.dropped", overlap.code());
    }

    @Test
    void a_failing_check_is_reported_not_thrown() {
        HasIssues failing = () -> { throw new IllegalStateException("bug"); };
        class Failing extends AbstractCorpusObject implements HasIssues {
            Failing() { super("g:f", span(0, 9, 1)); }
            @Override public List<Issue> issues() { return failing.issues(); }
        }
        Text text = Text.builder(A, CONTENT).add("g", List.of(new Failing()), List.of()).build();
        assertEquals(1, text.diagnostics(Diagnostic.Category.SEMANTIC).size());
    }
}
