package fr.cnrs.lacito.matchbox.corpus.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Issue;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The structural checks of {@link SimpleDependencyTree} ({@code HasIssues}). */
class SimpleDependencyTreeTest {

    private static final TextId A = new TextId(Path.of("a.conllu"));

    /** A tree of tokens "id:head" (head "_" for unspecified), one line of 10 characters per token. */
    private static SimpleDependencyTree tree(String... tokens) {
        List<Map<String, String>> list = new ArrayList<>();
        List<Integer> heads = new ArrayList<>();
        List<TextCoordinate> spans = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String[] parts = tokens[i].split(":");
            Map<String, String> t = new LinkedHashMap<>();
            t.put("ID", parts[0]);
            t.put("HEAD", parts[1]);
            list.add(t);
            int head;
            try {
                head = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                head = -1;
            }
            heads.add(head);
            spans.add(new TextCoordinate(A, i * 11, i * 11 + 9, i + 2, i + 2));
        }
        return new SimpleDependencyTree("conll:sentence", new TextCoordinate(A, 0, tokens.length * 11, 1, tokens.length + 1),
            "s1", Map.of(), list, heads, spans);
    }

    private static List<String> codes(SimpleDependencyTree t) {
        return t.issues().stream().map(Issue::code).toList();
    }

    @Test
    void a_valid_tree_has_no_issue() {
        assertTrue(tree("1:2", "2:0", "3:2").issues().isEmpty());
        // multiword range and empty node are not checked
        assertTrue(tree("1-2:_", "1:2", "2:0", "2.1:_").issues().isEmpty());
        // unspecified heads are not reported
        assertTrue(tree("1:_", "2:_").issues().isEmpty());
    }

    @Test
    void ids_out_of_order() {
        SimpleDependencyTree t = tree("1:0", "3:1");
        assertEquals(List.of(SimpleDependencyTree.CODE_IDS), codes(t));
        assertEquals(11, t.issues().get(0).span().orElseThrow().startIndex());
    }

    @Test
    void head_outside_the_sentence_or_not_a_number() {
        SimpleDependencyTree t = tree("1:0", "2:31", "3:x");
        assertEquals(List.of(SimpleDependencyTree.CODE_HEAD_RANGE, SimpleDependencyTree.CODE_HEAD), codes(t));
        assertEquals("HEAD 31 of token 2 is outside the sentence (0–3)", t.issues().get(0).message());
    }

    @Test
    void roots() {
        assertEquals(List.of(SimpleDependencyTree.CODE_NO_ROOT, SimpleDependencyTree.CODE_CYCLE),
            codes(tree("1:2", "2:1")));
        SimpleDependencyTree several = tree("1:0", "2:0");
        assertEquals(List.of(SimpleDependencyTree.CODE_ROOTS), codes(several));
        assertEquals(Diagnostic.Severity.WARNING, several.issues().get(0).severity());
    }

    @Test
    void cycles_are_reported_once() {
        SimpleDependencyTree t = tree("1:0", "2:3", "3:4", "4:2");
        assertEquals(List.of(SimpleDependencyTree.CODE_CYCLE), codes(t));
        assertEquals("Cycle of heads: 2 → 3 → 4 → 2", t.issues().get(0).message());
        assertEquals(List.of(SimpleDependencyTree.CODE_CYCLE), codes(tree("1:0", "2:2")));
    }

    @Test
    void without_token_spans_issues_are_on_the_sentence() {
        SimpleDependencyTree t = new SimpleDependencyTree("conll:sentence", new TextCoordinate(A, 0, 20, 1, 2),
            "s1", Map.of(), List.of(Map.of("ID", "1", "HEAD", "5")), List.of(5));
        assertTrue(t.issues().get(0).span().isEmpty());
    }
}
