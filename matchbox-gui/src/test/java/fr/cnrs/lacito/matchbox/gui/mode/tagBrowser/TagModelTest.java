package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TagModelTest {

    private static final String A = """
        <mami> {pig} #"pos/noun"
        <jaflo> {canoe} #"pos/noun"

        :::
        a mio-jaflo-m-wi-j
        1sg down-canoe-top-m.sg-pred
        'I went down in canoe' #"pos/verb/transitive"
        :::
        #"to check"
        """;
    private static final String B = """
        notes #"pos/verb" #"pos / verb/transitive" #"Pos"
        """;

    private final Corpus corpus = TaggedTexts.corpus("a.txt", A, "b.txt", B);
    private final TagIndex index = TagIndex.of(corpus);

    @Test
    void theTreeCountsOwnAndSubtreeTags() {
        TagNode root = TagTree.of(index.all(), TagTree.Sort.NAME);
        assertEquals(7, root.total());
        TagNode pos = root.find(List.of("pos")).orElseThrow();
        assertEquals(0, pos.own());
        assertEquals(5, pos.total());
        TagNode verb = root.find(List.of("pos", "verb")).orElseThrow();
        assertEquals(1, verb.own(), "a node can be a tag and a prefix");
        assertEquals(3, verb.total());
        // "pos / verb/transitive" and "pos/verb/transitive" are one node
        assertEquals(2, root.find(List.of("pos", "verb", "transitive")).orElseThrow().own());
        // exact grouping: "Pos" is another node
        assertEquals(1, root.find(List.of("Pos")).orElseThrow().total());
        // A–Z ignoring case, upper case first on ties
        assertEquals(List.of("Pos", "pos", "to check"), root.children().stream().map(TagNode::level).toList());
    }

    @Test
    void sortingByCountPutsTheBiggestFirst() {
        TagNode root = TagTree.of(index.all(), TagTree.Sort.COUNT);
        assertEquals(List.of("pos", "Pos", "to check"), root.children().stream().map(TagNode::level).toList());
        assertEquals(List.of("verb", "noun"),
            root.find(List.of("pos")).orElseThrow().children().stream().map(TagNode::level).toList());
    }

    @Test
    void theFilterKeepsAncestorsAndCounts() {
        TagNode root = TagTree.filter(TagTree.of(index.all(), TagTree.Sort.NAME), "TRANS");
        assertEquals(List.of("pos"), root.children().stream().map(TagNode::level).toList());
        TagNode verb = root.find(List.of("pos", "verb")).orElseThrow();
        assertEquals(3, verb.total());
        assertEquals(List.of("transitive"), verb.children().stream().map(TagNode::level).toList());
        assertTrue(TagTree.filter(root, "nothing").children().isEmpty());
    }

    @Test
    void theIndexListsExactTagsOrTheSubtree() {
        assertEquals(1, index.at(List.of("pos", "verb"), false).size());
        assertEquals(3, index.at(List.of("pos", "verb"), true).size());
        assertEquals(0, index.at(List.of("pos"), false).size());
    }

    @Test
    void updatingOneTextLeavesTheOthers() {
        corpus.put(TaggedTexts.parse(TaggedTexts.id("b.txt"), "only #\"new\"\n"));
        index.update(TaggedTexts.id("b.txt"), corpus.text(TaggedTexts.id("b.txt")));
        assertEquals(5, index.size());
        index.update(TaggedTexts.id("a.txt"), Optional.empty());
        assertEquals(List.of("new"), index.all().stream().map(TagOccurrence::keyword).toList());
    }

    @Test
    void targetsAreClassifiedByCapability() {
        List<String> types = index.all().stream().filter(o -> o.text().equals(TaggedTexts.id("a.txt")))
            .map(TagOccurrence::targetType).toList();
        assertEquals(List.of("Lexical entry", "Lexical entry", "Interlinear sentence", "Interlinear sentence"), types);
        assertEquals(TagTargetTypes.WHOLE_TEXT, TagTargetTypes.of(Optional.empty()));
    }

    @Test
    void rowsHaveAHeaderPerFile() {
        List<OccurrenceRow> rows = OccurrenceRow.of(index.all());
        assertEquals(new OccurrenceRow.FileHeader(TaggedTexts.id("a.txt"), 4), rows.get(0));
        assertEquals(new OccurrenceRow.FileHeader(TaggedTexts.id("b.txt"), 3), rows.get(5));
        assertEquals(9, rows.size());
    }

    @Test
    void csvEscapesAndHasAHeader() {
        String csv = OccurrenceExport.csv(index.at(List.of("pos", "verb"), false), corpus);
        String[] lines = csv.split("\r\n");
        assertEquals("keyword,file,line,applies to,target,context", lines[0]);
        assertTrue(lines[1].startsWith("pos/verb,"));
        assertTrue(lines[1].endsWith(",\"notes #\"\"pos/verb\"\" #\"\"pos / verb/transitive\"\" #\"\"Pos\"\"\""), lines[1]);
        assertEquals("\"a,b\"", OccurrenceExport.escape("a,b"));
        assertEquals("plain", OccurrenceExport.escape("plain"));
    }

    @Test
    void excerptsAreCutAtTheTextBoundaries() {
        String text = "l1\r\nl2\nl3\rl4\nl5";
        int l3 = text.indexOf("l3");
        ContextExcerpt e = ContextExcerpt.of(text, l3, l3 + 2, 1, 1);
        assertEquals(List.of("l2", "l3", "l4"), e.lines().stream().map(ContextExcerpt.Line::text).toList());
        assertEquals(List.of(2, 3, 4), e.lines().stream().map(ContextExcerpt.Line::number).toList());
        assertEquals(List.of("l1", "l2"), ContextExcerpt.of(text, 0, 2, 5, 1).lines().stream().map(ContextExcerpt.Line::text).toList());
        assertEquals(List.of("l5"), ContextExcerpt.of(text, text.length() - 1, text.length(), 0, 0).lines().stream()
            .map(ContextExcerpt.Line::text).toList());
        assertFalse(ContextExcerpt.of("", 0, 0, 3, 3).lines().isEmpty());
    }
}
