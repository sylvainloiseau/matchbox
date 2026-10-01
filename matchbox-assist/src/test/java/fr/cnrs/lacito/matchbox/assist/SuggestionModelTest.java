package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.assist.suggestion.EditLocator;
import fr.cnrs.lacito.matchbox.assist.suggestion.GrammarQuickFixes;
import fr.cnrs.lacito.matchbox.assist.suggestion.Relocation;
import fr.cnrs.lacito.matchbox.assist.suggestion.Suggestion;
import fr.cnrs.lacito.matchbox.assist.suggestion.TextEdit;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SuggestionModelTest {

    private static final Path FILE = Path.of("f.txt");

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
    void relocationShiftsKeepsOrSearches() {
        String text = "one two three";
        TextEdit edit = TextEdit.replace(text, 4, 7, "2");
        // insertion before
        String t1 = "xx" + text;
        assertEquals(Optional.of(edit.shifted(2)), Relocation.relocate(edit, 0, 0, 2, t1));
        // change after
        String t2 = text + "!";
        assertEquals(Optional.of(edit), Relocation.relocate(edit, 13, 0, 1, t2));
        // overlapping change that keeps the original nearby
        String t3 = "one  two three";
        assertEquals(5, Relocation.relocate(edit, 3, 1, 2, t3).orElseThrow().start());
        // overlapping change that destroys it
        String t4 = "one tw three";
        assertTrue(Relocation.relocate(edit, 6, 1, 0, t4).isEmpty());
    }

    @Test
    void suggestionsBecomeOutdatedWhenTheirTextDisappears() {
        String text = "abc [def";
        Suggestion s = new Suggestion("1", Suggestion.Source.GRAMMAR_FIX, 8, 8, "Insert ']'", "",
            List.of(TextEdit.insert(8, "]")), Optional.empty(), Suggestion.Status.ACTIVE, "");
        Suggestion moved = s.relocated(0, 0, 1, "Xabc [def");
        assertEquals(9, moved.start());
        assertEquals(9, moved.edits().get(0).start());
        Suggestion replaced = new Suggestion("2", Suggestion.Source.AI, 0, 3, "t", "",
            List.of(TextEdit.replace(text, 0, 3, "ABC")), Optional.empty(), Suggestion.Status.ACTIVE, "");
        assertEquals(Suggestion.Status.OUTDATED, replaced.relocated(1, 1, 0, "ac [def").status());
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
        List<ParseError> errors = new BracketGrammar().getResult(FILE, text).errors();
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
