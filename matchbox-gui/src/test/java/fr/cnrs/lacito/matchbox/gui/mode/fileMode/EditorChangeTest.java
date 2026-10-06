package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import org.fxmisc.richtext.model.PlainTextChange;
import org.junit.jupiter.api.Test;

class EditorChangeTest {

    @Test
    void codeAreaChangesBecomeTextEdits() {
        String before = "hello world";
        // typing "big " before "world"
        TextEdit insert = EditorController.toEdit(new PlainTextChange(6, "", "big "));
        assertEquals("hello big world", insert.apply(before));
        // replacing the selection "hello" by "bye"
        TextEdit replace = EditorController.toEdit(new PlainTextChange(0, "hello", "bye"));
        assertEquals(new TextEdit(0, 5, "hello", "bye"), replace);
        assertEquals("bye world", replace.apply(before));
    }
}
