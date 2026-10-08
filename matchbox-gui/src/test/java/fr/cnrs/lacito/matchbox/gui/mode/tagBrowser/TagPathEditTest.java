package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagPathEditTest {

    private final Corpus corpus = TaggedTexts.corpus(
        "a.txt", "x #\"pos/verb\" #\"pos/verb/transitive\" #\"pos/verbal\"\n",
        "b.txt", "y #\"pos/verb\" #\"lexical/verb/transitive\" #\"syntax\"\n");
    private final List<TagOccurrence> all = TagIndex.of(corpus).all();

    @Test
    void renameRewritesThePrefixOnly() {
        TagPathEdit.Change c = assertInstanceOf(TagPathEdit.Change.class,
            TagPathEdit.rename(all, List.of("pos", "verb"), " action "));
        assertEquals(List.of("pos", "action"), c.to());
        assertEquals(List.of(new TagPathEdit.Rewrite("pos/verb", "pos/action", 2),
            new TagPathEdit.Rewrite("pos/verb/transitive", "pos/action/transitive", 1)), c.rewrites());
        assertEquals(3, c.count(), "pos/verbal untouched");
        assertEquals(Set.of(TaggedTexts.id("a.txt"), TaggedTexts.id("b.txt")), c.files());
        assertEquals(0, c.merged());
        assertEquals("Rename tag pos/verb → pos/action", c.description());
    }

    @Test
    void renameIsPlain() {
        assertEquals("A rename stays at the same level: the name cannot contain \"/\"",
            refusal(TagPathEdit.rename(all, List.of("pos", "verb"), "a/b")));
        assertEquals("pos › verbal exists", refusal(TagPathEdit.rename(all, List.of("pos", "verb"), "verbal")));
        assertEquals("The name cannot be empty", refusal(TagPathEdit.rename(all, List.of("pos", "verb"), "  ")));
        assertEquals("The name is unchanged", refusal(TagPathEdit.rename(all, List.of("pos", "verb"), "verb")));
    }

    @Test
    void moveKeepsTheNameAndMerges() {
        TagPathEdit.Change c = assertInstanceOf(TagPathEdit.Change.class,
            TagPathEdit.move(all, List.of("pos", "verb"), List.of("lexical")));
        assertEquals(List.of("lexical", "verb"), c.to());
        assertEquals(3, c.count());
        assertEquals(1, c.merged(), "lexical/verb/transitive exists");
        assertEquals("lexical/verb/transitive", c.rewrites().get(1).to());
    }

    @Test
    void moveOfATerminalNodeAndToTheTopLevel() {
        TagPathEdit.Change leaf = assertInstanceOf(TagPathEdit.Change.class,
            TagPathEdit.move(all, List.of("pos", "verb", "transitive"), List.of("syntax")));
        assertEquals(List.of(new TagPathEdit.Rewrite("pos/verb/transitive", "syntax/transitive", 1)), leaf.rewrites());
        TagPathEdit.Change top = assertInstanceOf(TagPathEdit.Change.class,
            TagPathEdit.move(all, List.of("pos", "verb"), List.of()));
        assertEquals("verb/transitive", top.rewrites().get(1).to());
    }

    @Test
    void moveIsRefusedOnItselfItsParentAndItsDescendants() {
        assertEquals("It is already under pos", refusal(TagPathEdit.move(all, List.of("pos", "verb"), List.of("pos"))));
        assertEquals("A tag cannot be moved under itself",
            refusal(TagPathEdit.move(all, List.of("pos", "verb"), List.of("pos", "verb"))));
        assertEquals("A tag cannot be moved under itself",
            refusal(TagPathEdit.move(all, List.of("pos"), List.of("pos", "verb"))));
        assertEquals("It is already at the top level", refusal(TagPathEdit.move(all, List.of("syntax"), List.of())));
    }

    private static String refusal(TagPathEdit.Plan plan) {
        return assertInstanceOf(TagPathEdit.Refused.class, plan).reason();
    }
}
