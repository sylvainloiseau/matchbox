package fr.cnrs.lacito.matchbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextDocumentTest {

    private static final Object EDITOR = new Object();
    private static final EditOrigin TYPING = EditOrigin.of(EditOrigin.Kind.TYPING, EDITOR);
    private static final EditOrigin VIEW = EditOrigin.of(EditOrigin.Kind.VIEW, "view");

    @TempDir
    Path dir;
    private TextDocument document;
    private final List<DocumentChange> changes = new ArrayList<>();
    private long now;
    private TextHistory history;

    @BeforeEach
    void setUp() throws IOException {
        document = new TextDocument(Files.writeString(dir.resolve("a.txt"), "hello world"));
        document.addChangeListener(changes::add);
        history = new TextHistory(document, () -> now);
    }

    @Test
    void editsAreAppliedAtomically() {
        String text = document.getCurrentText();
        assertTrue(document.applyEdits(List.of(TextEdit.replace(text, 0, 5, "HELLO"), TextEdit.replace(text, 6, 11, "W")), VIEW));
        assertEquals("HELLO W", document.getCurrentText());
        assertEquals(1, document.getVersion());
        assertEquals(1, changes.size());
        assertEquals(VIEW, changes.get(0).origin());

        // the second edit no longer matches: nothing applied
        assertFalse(document.applyEdits(List.of(TextEdit.replace("HELLO W", 0, 1, "J"),
            new TextEdit(6, 7, "X", "Y")), VIEW));
        assertEquals("HELLO W", document.getCurrentText());
        assertEquals(1, changes.size());
    }

    @Test
    void sequentialEditsAreOneChange() {
        // move "world" before "hello": delete then insert
        TextEdit delete = new TextEdit(5, 11, " world", "");
        TextEdit insert = TextEdit.insert(0, "world ");
        assertTrue(document.applySequential(List.of(delete, insert), TYPING));
        assertEquals("world hello", document.getCurrentText());
        assertEquals(1, changes.size());
        assertTrue(history.undo());
        assertEquals("hello world", document.getCurrentText());
    }

    @Test
    void dirtyStateFollowsTheSavedText() throws IOException {
        assertFalse(document.getCouldBeWritten());
        document.replaceAll("hello there", TYPING);
        assertTrue(document.getCouldBeWritten());
        assertTrue(document.getCouldBeReparsed());
        history.undo();
        assertFalse(document.getCouldBeWritten(), "back to the saved text");
        document.replaceAll("saved", TYPING);
        document.writeFile();
        assertFalse(document.getCouldBeWritten());
        assertEquals("saved", Files.readString(document.getFilePath()));
    }

    @Test
    void markParsedOnlyForTheCurrentVersion() {
        document.replaceAll("v1", TYPING);
        long v1 = document.getVersion();
        document.replaceAll("v2", TYPING);
        document.markParsed(v1);
        assertTrue(document.getCouldBeReparsed());
        document.markParsed(document.getVersion());
        assertFalse(document.getCouldBeReparsed());
    }

    @Test
    void consecutiveTypingIsMergedViewEditsAreNot() {
        type(11, "!");
        now += 100;
        type(12, "!");
        now += 100;
        String text = document.getCurrentText();
        document.applyEdits(List.of(TextEdit.replace(text, 0, 5, "HELLO")), VIEW);
        assertEquals("HELLO world!!", document.getCurrentText());

        assertTrue(history.undo());
        assertEquals("hello world!!", document.getCurrentText());
        assertTrue(history.undo());
        assertEquals("hello world", document.getCurrentText(), "both characters in one step");
        assertFalse(history.undo());

        assertTrue(history.redo());
        assertEquals("hello world!!", document.getCurrentText());
        assertTrue(history.redo());
        assertEquals("HELLO world!!", document.getCurrentText());
        assertFalse(history.redoAvailableProperty().get());
    }

    @Test
    void typingAfterADelayIsANewStep() {
        type(11, "!");
        now += TextHistory.MERGE_DELAY_MS + 1;
        type(12, "?");
        history.undo();
        assertEquals("hello world!", document.getCurrentText());
    }

    @Test
    void aNewChangeClearsRedo() {
        type(11, "!");
        history.undo();
        assertTrue(history.redoAvailableProperty().get());
        type(0, ">");
        assertFalse(history.redoAvailableProperty().get());
    }

    @Test
    void undoOfMultiEditChange() {
        String text = document.getCurrentText();
        document.applyEdits(List.of(TextEdit.replace(text, 0, 1, "JJJ"), TextEdit.replace(text, 6, 11, "")), VIEW);
        assertEquals("JJJello ", document.getCurrentText());
        history.undo();
        assertEquals("hello world", document.getCurrentText());
    }

    @Test
    void minimalEditKeepsCommonPrefixAndSuffix() {
        TextEdit e = TextDocument.minimalEdit("abcXYdef", "abcZdef").orElseThrow();
        assertEquals(new TextEdit(3, 5, "XY", "Z"), e);
        assertTrue(TextDocument.minimalEdit("same", "same").isEmpty());
        assertEquals("aaaa", TextDocument.minimalEdit("aa", "aaaa").orElseThrow().apply("aa"));
    }

    private void type(int offset, String s) {
        document.applyEdits(List.of(TextEdit.insert(offset, s)), TYPING);
    }
}
