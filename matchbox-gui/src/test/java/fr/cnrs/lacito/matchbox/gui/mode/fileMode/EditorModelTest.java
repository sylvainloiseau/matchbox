package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorKeymap.Binding;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorKeymap.Platform;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FindModel.Query;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;

/** The pure classes of the editor: keymap, lines, indentation, find, invisibles, gutter, palette. */
class EditorModelTest {

    private static String apply(String text, TextEdit e) {
        return text.substring(0, e.start()) + e.replacement() + text.substring(e.end());
    }

    private static EditorAction action(Platform p, KeyCodeCombination key, boolean select) {
        return EditorKeymap.forPlatform(p).stream()
            .filter(b -> b.key().equals(key) && b.select() == select).map(Binding::action).findFirst().orElse(null);
    }

    @Test
    void theKeymapFollowsTheConventionsOfEachSystem() {
        assertEquals(EditorAction.LINE_END, action(Platform.MAC, new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.META_DOWN), false));
        assertEquals(EditorAction.WORD_RIGHT, action(Platform.MAC, new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN), false));
        assertEquals(EditorAction.FILE_END, action(Platform.MAC, new KeyCodeCombination(KeyCode.DOWN, KeyCombination.META_DOWN), false));
        assertEquals(EditorAction.LINE_START, action(Platform.MAC, new KeyCodeCombination(KeyCode.HOME), false), "Home kept");
        assertEquals(EditorAction.WORD_RIGHT, action(Platform.OTHER, new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN), false));
        assertEquals(EditorAction.FILE_START, action(Platform.OTHER, new KeyCodeCombination(KeyCode.HOME, KeyCombination.CONTROL_DOWN), false));
        assertEquals(EditorAction.LINE_END,
            action(Platform.OTHER, new KeyCodeCombination(KeyCode.END, KeyCombination.SHIFT_DOWN), true), "Shift selects");
        assertNull(action(Platform.OTHER, new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.META_DOWN), false));
        for (Platform p : Platform.values()) {
            Set<KeyCodeCombination> keys = new HashSet<>();
            for (Binding b : EditorKeymap.forPlatform(p)) assertTrue(keys.add(b.key()), "one action per key: " + b.key());
            for (EditorAction a : EditorAction.values()) {
                if (a.isMove()) {
                    assertTrue(EditorKeymap.forPlatform(p).stream().anyMatch(b -> b.action() == a && b.select()), a + " selects");
                }
            }
        }
        assertEquals("⇧⌘→", EditorKeymap.display(new Binding(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.META_DOWN,
            KeyCombination.SHIFT_DOWN), EditorAction.LINE_END, true), Platform.MAC));
    }

    @Test
    void lineIndex() {
        LineIndex i = new LineIndex("ab\ncd\n");
        assertEquals(3, i.lineCount());
        assertEquals(1, i.lineOf(3));
        assertEquals(1, i.lineOf(5));
        assertEquals(2, i.lineOf(6));
        assertEquals("cd", i.line(1));
        assertEquals(5, i.end(1));
    }

    @Test
    void duplicateDeleteAndMoveLines() {
        String t = "one\ntwo\nthree";
        LineCommands.Result d = LineCommands.duplicate(t, 5, 5);
        assertEquals("one\ntwo\ntwo\nthree", apply(t, d.edit()));
        assertEquals(9, d.selectionStart(), "the caret follows the copy");
        assertEquals("two\nthree", apply(t, LineCommands.delete(t, 0, 2).edit()));
        LineCommands.Result last = LineCommands.delete(t, 10, 10);
        assertEquals("one\ntwo", apply(t, last.edit()));
        assertEquals(4, last.selectionStart(), "caret at the start of the line above");
        assertEquals("", apply("only", LineCommands.delete("only", 0, 0).edit()));
        assertEquals("two\none\nthree", apply(t, LineCommands.moveUp(t, 5, 5).orElseThrow().edit()));
        assertTrue(LineCommands.moveUp(t, 1, 1).isEmpty());
        assertEquals("one\nthree\ntwo", apply(t, LineCommands.moveDown(t, 5, 5).orElseThrow().edit()));
        assertTrue(LineCommands.moveDown(t, 9, 9).isEmpty());
        // a selection of two lines ending at the start of the third moves two lines only
        assertEquals("three\none\ntwo", apply(t, LineCommands.moveDown(t, 0, 8).orElseThrow().edit()));
        assertEquals("one\ntwo\none\ntwo\nthree", apply(t, LineCommands.duplicate(t, 0, 8).edit()));
    }

    @Test
    void autoIndentAndSmartHome() {
        assertEquals("\n    ", Indentation.newline("    a b", 7));
        assertEquals("\n  ", Indentation.newline("    a b", 2), "Enter inside the indentation adds no more than before the caret");
        assertEquals(4, Indentation.smartHome("    abc", 7));
        assertEquals(0, Indentation.smartHome("    abc", 4));
        assertEquals(0, Indentation.smartHome("   ", 2));
    }

    @Test
    void findWithOptions() {
        String t = "Mio mio miot (mio)";
        assertEquals(4, FindModel.find(t, new Query("mio", false, false, false)).matches().size());
        assertEquals(3, FindModel.find(t, new Query("mio", true, false, false)).matches().size());
        assertEquals(3, FindModel.find(t, new Query("mio", false, true, false)).matches().size(), "whole word");
        assertEquals(2, FindModel.find(t, new Query("m.o\\b", true, false, true)).matches().size());
        FindModel.Result bad = FindModel.find(t, new Query("(", false, false, true));
        assertTrue(bad.matches().isEmpty());
        assertNotNull(bad.problem());
        assertTrue(FindModel.find(t, new Query("^", false, false, true)).matches().isEmpty(), "empty matches skipped");
        FindModel.Result r = FindModel.find(t, new Query("mio", false, false, false));
        assertEquals(1, r.nextFrom(1));
        assertEquals(0, r.nextFrom(100), "wraps");
        assertEquals(3, r.previousFrom(0), "wraps backwards");
        List<TextEdit> edits = FindModel.replaceAll("a-b c-d", new Query("(\\w)-(\\w)", false, false, true), "$2=$1");
        assertEquals(List.of("b=a", "d=c"), edits.stream().map(TextEdit::replacement).toList());
    }

    @Test
    void invisibleCharacters() {
        assertEquals(List.of(new Invisibles.Span(1, 2), new Invisibles.Span(5, 7)), Invisibles.spans("a\tb c  \nd"));
        assertTrue(Invisibles.spans("a b").isEmpty());
    }

    @Test
    void gutterMarkers() {
        TextId id = TextId.of(Path.of("a.txt"));
        LineIndex index = new LineIndex("l0\nl1\nl2");
        TextAction action = new TextAction("Add header", "", 3, List.of(), 0);
        Diagnostic error = new Diagnostic(id, "g", 3, 0, "unexpected", -1, -1, List.of());
        Map<Integer, GutterLayout.Markers> m = GutterLayout.markers(index, List.of(), List.of(action), List.of(error));
        assertEquals(List.of(1, 2), List.copyOf(m.keySet()));
        assertEquals(List.of(action), m.get(1).actions());
        assertEquals(List.of("unexpected"), m.get(2).errors());
        assertEquals(2, GutterLayout.digits(9));
        assertEquals(4, GutterLayout.digits(1000));
        assertEquals(0.5, GutterLayout.rulerPosition(0, 1));
    }

    @Test
    void warningsAreMarkedApartAndErrorsWin() {
        TextId id = TextId.of(Path.of("a.txt"));
        LineIndex index = new LineIndex("l0\nl1\nl2");
        Diagnostic warning = Diagnostic.warning(id, "g", 2, 0, "Not kept", 3, 4);
        Diagnostic error = new Diagnostic(id, "g", 3, 0, "unexpected", 6, 6, List.of());
        Diagnostic warningOnErrorLine = Diagnostic.warning(id, "g", 3, 0, "Not kept either", 6, 7);
        Map<Integer, GutterLayout.Markers> m = GutterLayout.markers(index, List.of(), List.of(),
            List.of(warning, error, warningOnErrorLine));
        assertTrue(m.get(1).warningOnly());
        assertTrue(m.get(1).errors().isEmpty());
        assertEquals(List.of(warning), m.get(1).warnings());
        assertFalse(m.get(2).warningOnly());
        assertEquals(List.of("unexpected", "Not kept either"), m.get(2).messages());
    }

    @Test
    void caretMemoryIsClampedToTheCurrentLength() {
        CaretMemory memory = new CaretMemory();
        TextId id = TextId.of(Path.of("a.txt"));
        memory.remember(id, new CaretMemory.Place(50, 40, 3));
        assertEquals(new CaretMemory.Place(10, 10, 3), memory.recall(id, 10).orElseThrow());
        memory.clear();
        assertTrue(memory.recall(id, 10).isEmpty());
    }

    @Test
    void thePaletteLoadsAndSearchesByName() {
        SpecialCharacters palette = SpecialCharacters.load();
        assertTrue(palette.groups().size() >= 5);
        assertTrue(palette.search("glottal stop").stream().anyMatch(e -> e.character().equals("ʔ")));
        assertEquals("ŋ", palette.search("ŋ").get(0).character());
        assertTrue(palette.search("combining tilde").stream().anyMatch(e -> e.display().equals("\u25CC\u0303")),
            "combining marks are shown on a dotted circle");
    }
}
