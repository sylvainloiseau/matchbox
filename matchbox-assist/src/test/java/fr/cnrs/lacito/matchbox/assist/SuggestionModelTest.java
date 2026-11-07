package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.assist.suggestion.EditLocator;
import fr.cnrs.lacito.matchbox.assist.suggestion.GrammarQuickFixes;
import fr.cnrs.lacito.matchbox.assist.suggestion.Relocation;
import fr.cnrs.lacito.matchbox.assist.suggestion.Suggestion;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SuggestionModelTest {

    private static final TextId FILE = new TextId(Path.of("f.txt"));

    @Test
    void textEditsApplyInAnyOrderAndCheckTheOriginal() {
        String text = "abc def ghi";
        List<TextEdit> edits = List.of(TextEdit.replace(text, 0, 3, "X"), TextEdit.replace(text, 8, 11, "YY"));
        assertEquals("X def YY", TextEdit.applyAll(text, edits));
        assertThrows(IllegalStateException.class, () -> TextEdit.replace(text, 0, 3, "X").apply("zzz def"));
        assertThrows(IllegalArgumentException.class,
            () -> TextEdit.applyAll(text, List.of(TextEdit.replace(text, 0, 5, ""), TextEdit.replace(text, 4, 6, ""))));
    }

    @Test
    void relocationAcceptsOnlyAUniqueNearbyCopy() {
        String text = "one two three";
        TextEdit edit = TextEdit.replace(text, 4, 7, "2");
        assertEquals(Optional.of(edit), Relocation.find(edit, text));
        assertEquals(6, Relocation.find(edit, "xx" + text).orElseThrow().start());
        // two copies: never a guess, even at the old offset
        assertTrue(Relocation.find(edit, "one two two three").isEmpty());
        // gone
        assertTrue(Relocation.find(edit, "one tw three").isEmpty());
        // too far
        assertTrue(Relocation.find(edit, "x".repeat(400) + text).isEmpty());
        // an insertion cannot be searched for
        TextEdit insertion = TextEdit.insert(3, "!");
        assertTrue(Relocation.find(insertion, text).isEmpty());
    }

    @Test
    void suggestionsBecomeOutdatedWhenTheirTextDisappears() {
        String text = "abc [def";
        Suggestion s = new Suggestion("1", Suggestion.Source.GRAMMAR_FIX, 8, 8, "Insert ']'", "",
            List.of(TextEdit.insert(8, "]")), Optional.empty(), Suggestion.Status.ACTIVE, "");
        Suggestion moved = s.relocated(List.of(TextEdit.insert(0, "X")), "Xabc [def");
        assertEquals(9, moved.start());
        assertEquals(9, moved.edits().get(0).start());
        // text typed at the same offset: the insertion goes after it
        assertEquals(9, s.relocated(List.of(TextEdit.insert(8, "!")), "abc [def!").edits().get(0).start());
        // a change after the range keeps it
        Suggestion early = new Suggestion("4", Suggestion.Source.AI, 0, 3, "t", "",
            List.of(TextEdit.replace(text, 0, 3, "ABC")), Optional.empty(), Suggestion.Status.ACTIVE, "");
        assertEquals(early, early.relocated(List.of(TextEdit.insert(8, "!")), "abc [def!"));
        Suggestion replaced = new Suggestion("2", Suggestion.Source.AI, 0, 3, "t", "",
            List.of(TextEdit.replace(text, 0, 3, "ABC")), Optional.empty(), Suggestion.Status.ACTIVE, "");
        assertEquals(Suggestion.Status.OUTDATED,
            replaced.relocated(List.of(TextEdit.replace(text, 1, 2, "")), "ac [def").status());
        // a repeated text far before does not confuse it
        assertEquals(103, replaced.relocated(List.of(TextEdit.insert(0, "abc".repeat(34) + "!")),
            "abc".repeat(34) + "!" + text).edits().get(0).start());
        // a suggestion without edits: its range follows
        Suggestion error = new Suggestion("3", Suggestion.Source.PARSE_ERROR, 4, 5, "e", "",
            List.of(), Optional.empty(), Suggestion.Status.ACTIVE, "");
        Suggestion e2 = error.relocated(List.of(TextEdit.insert(0, "zz")), "zz" + text);
        assertEquals(6, e2.start());
        assertEquals(7, e2.end());
    }

    @Test
    void locatorNeedsAUniqueOrNearestOccurrence() {
        String text = "ka ka ti ka";
        assertEquals(6, EditLocator.locate(text, 0, text.length(), "ti", "TI", -1).orElseThrow().start());
        assertTrue(EditLocator.locate(text, 0, text.length(), "ka", "KA", -1).isEmpty(), "ambiguous");
        assertEquals(9, EditLocator.locate(text, 0, text.length(), "ka", "KA", 10).orElseThrow().start());
        assertTrue(EditLocator.locate(text, 0, text.length(), "zz", "ZZ", -1).isEmpty());
        assertTrue(EditLocator.locate(text, 0, text.length(), "ti", "ti", -1).isEmpty(), "no change");
        assertTrue(EditLocator.locate(text, 0, 5, "ti", "TI", -1).isEmpty(), "outside the scope");
    }

    @Test
    void quickFixesInsertLiteralsAndDeleteExtraneousInput() {
        String text = "a [b\nc]";
        List<Diagnostic> errors = new BracketGrammar().getResult(FILE, text).diagnostics();
        List<GrammarQuickFixes.Fix> missing = GrammarQuickFixes.candidates(text, errors.get(0));
        assertEquals(1, missing.size());
        assertEquals("a [b]\nc]", missing.get(0).edit().apply(text));
        List<GrammarQuickFixes.Fix> extraneous = GrammarQuickFixes.candidates(text, errors.get(1));
        assertEquals("a [b\nc", extraneous.get(0).edit().apply(text));
        assertTrue(extraneous.get(0).title().startsWith("Delete"));
    }

    @Test
    void literalsAreUnescaped() {
        assertEquals("'''", GrammarQuickFixes.literal("'\\'\\'\\''"));
        assertEquals("\n", GrammarQuickFixes.literal("'\\n'"));
        assertEquals("é", GrammarQuickFixes.literal("'\\u00E9'"));
        assertNull(GrammarQuickFixes.literal("IMB_LINE"));
        assertNull(GrammarQuickFixes.literal("<EOF>"));
    }
}
