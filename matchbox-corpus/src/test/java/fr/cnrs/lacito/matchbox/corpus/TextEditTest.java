package fr.cnrs.lacito.matchbox.corpus;

import static fr.cnrs.lacito.matchbox.corpus.CorpusTest.span;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TextEditTest {

    private static final TextId T = new TextId(Path.of("t"));

    @Test
    void replaceUsesTheInclusiveStopIndex() {
        String text = "say 'hello' now";
        TextEdit edit = TextEdit.replace(text, span(T, 5, 9), "bye");
        assertEquals("say 'bye' now", edit.apply(text));
    }

    @Test
    void emptySpanIsAnInsertion() {
        String text = "say '' now";
        TextCoordinate empty = span(T, 5, 4);
        assertTrue(empty.isEmpty());
        assertTrue(empty.isValidFor(text.length()));
        assertTrue(span(T, 4, 5).encloses(empty));
        assertTrue(span(T, 0, 4).encloses(empty), "insertion right after the parent's end");
        assertFalse(span(T, 6, 9).encloses(empty));
        assertEquals("say 'hi' now", TextEdit.replace(text, empty, "hi").apply(text));
    }

    @Test
    void inverseUndoesTheEdit() {
        String text = "abcdef";
        TextEdit edit = TextEdit.replace(text, 1, 3, "XYZW");
        String edited = edit.apply(text);
        assertEquals("aXYZWdef", edited);
        assertEquals(text, edit.inverse().apply(edited));
    }
}
