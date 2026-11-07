package fr.cnrs.lacito.matchbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private EditJournal history;

    @BeforeEach
    void setUp() throws IOException {
        document = new TextDocument(Files.writeString(dir.resolve("a.txt"), "hello world"));
        document.addChangeListener(changes::add);
        history = new EditJournal(() -> now);
        history.track(document);
    }

    @Test
    void editsAreAppliedAtomically() {
        String text = document.getCurrentText();
        long before = document.getVersion();
        assertTrue(document.applyEdits(List.of(TextEdit.replace(text, 0, 5, "HELLO"), TextEdit.replace(text, 6, 11, "W")), VIEW));
        assertEquals("HELLO W", document.getCurrentText());
        assertTrue(document.getVersion() != before);
        assertEquals(before, changes.get(0).versionBefore());
        assertEquals(document.getVersion(), changes.get(0).versionAfter());
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
        assertTrue(history.undo().isPresent());
        assertEquals("hello world", document.getCurrentText());
    }

    @Test
    void dirtyStateFollowsTheSavedText() throws IOException {
        assertFalse(document.getCouldBeWritten());
        document.replaceAll("hello there", TYPING);
        assertTrue(document.getCouldBeWritten());
        assertTrue(document.getCouldBeReparsed());
        history.undo().isPresent();
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

        assertTrue(history.undo().isPresent());
        assertEquals("hello world!!", document.getCurrentText());
        assertTrue(history.undo().isPresent());
        assertEquals("hello world", document.getCurrentText(), "both characters in one step");
        assertFalse(history.undo().isPresent());

        assertTrue(history.redo().isPresent());
        assertEquals("hello world!!", document.getCurrentText());
        assertTrue(history.redo().isPresent());
        assertEquals("HELLO world!!", document.getCurrentText());
        assertFalse(history.redoAvailableProperty().get());
    }

    @Test
    void typingAfterADelayIsANewStep() {
        type(11, "!");
        now += EditJournal.MERGE_DELAY_MS + 1;
        type(12, "?");
        history.undo().isPresent();
        assertEquals("hello world!", document.getCurrentText());
    }

    @Test
    void aNewChangeClearsRedo() {
        type(11, "!");
        history.undo().isPresent();
        assertTrue(history.redoAvailableProperty().get());
        type(0, ">");
        assertFalse(history.redoAvailableProperty().get());
    }

    @Test
    void undoOfMultiEditChange() {
        String text = document.getCurrentText();
        document.applyEdits(List.of(TextEdit.replace(text, 0, 1, "JJJ"), TextEdit.replace(text, 6, 11, "")), VIEW);
        assertEquals("JJJello ", document.getCurrentText());
        history.undo().isPresent();
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

    // ------------------------------------------------------------------
    // Versions and rebase
    // ------------------------------------------------------------------

    @Test
    void twoDocumentsNeverShareAVersion() throws IOException {
        TextDocument other = new TextDocument(Files.writeString(dir.resolve("b.txt"), "hello world"));
        assertNotEquals(document.getVersion(), other.getVersion());
        long v = other.getVersion();
        type(other, 0, "x");
        assertNotEquals(v, document.getVersion());
        // a version of another document is unknown here
        assertTrue(document.changesSince(v).isEmpty());
        assertTrue(document.changesSince(document.getVersion()).orElseThrow().isEmpty());
    }

    @Test
    void anEditComputedBeforeTypingIsMoved() {
        String parsed = document.getCurrentText();
        long version = document.getVersion();
        TextEdit world = TextEdit.replace(parsed, 6, 11, "there");
        type(document, 0, "oh, ");
        type(document, 4, "well ");
        assertEquals(2, document.changesSince(version).orElseThrow().size());
        TextDocument.Rebased r = document.rebase(List.of(world), version, parsed);
        List<TextEdit> moved = assertInstanceOf(TextDocument.Rebased.Moved.class, r).edits();
        assertTrue(document.applyEdits(moved, VIEW));
        assertEquals("oh, well hello there", document.getCurrentText());
    }

    @Test
    void anInsertionIsMovedAndRepeatedTextIsNotConfused() throws IOException {
        TextDocument twice = new TextDocument(Files.writeString(dir.resolve("c.txt"), "pig pig"));
        String parsed = twice.getCurrentText();
        long version = twice.getVersion();
        TextEdit second = TextEdit.replace(parsed, 4, 7, "dog");
        TextEdit insertion = TextEdit.insert(7, "!");
        type(twice, 0, "pig ");
        List<TextEdit> moved = assertInstanceOf(TextDocument.Rebased.Moved.class,
            twice.rebase(List.of(second, insertion), version, parsed)).edits();
        assertTrue(twice.applyEdits(moved, VIEW));
        assertEquals("pig pig dog!", twice.getCurrentText());
    }

    @Test
    void typingInsideTheRangeIsAConflictUnlessUndone() {
        document.applyEdits(List.of(TextEdit.insert(11, "\nsecond line")), VIEW);
        String parsed = document.getCurrentText();
        long version = document.getVersion();
        TextEdit second = TextEdit.replace(parsed, 12, 18, "2nd");
        type(document, 14, "x");
        TextDocument.Rebased.Conflict c = assertInstanceOf(TextDocument.Rebased.Conflict.class,
            document.rebase(List.of(second), version, parsed));
        assertEquals(2, c.line());
        // delete the typed character: the net change is empty
        document.applyEdits(List.of(TextEdit.replace(document.getCurrentText(), 14, 15, "")), TYPING);
        assertInstanceOf(TextDocument.Rebased.Moved.class, document.rebase(List.of(second), version, parsed));
    }

    @Test
    void forgottenHistoryIsUnknown() {
        ChangeLog log = new ChangeLog(2);
        long v0 = 100;
        for (int i = 0; i < 3; i++) {
            log.record(new DocumentChange(document, List.of(TextEdit.insert(0, "x")), TYPING, v0 + i, v0 + i + 1));
        }
        assertEquals(2, log.size());
        assertTrue(log.since(v0, v0 + 3).isEmpty());
        assertEquals(2, log.since(v0 + 1, v0 + 3).orElseThrow().size());
        assertEquals(v0 + 1, log.since(v0 + 1, v0 + 3).orElseThrow().get(0).versionBefore());
        // a gap clears the log
        log.record(new DocumentChange(document, List.of(TextEdit.insert(0, "x")), TYPING, 500, 501));
        assertTrue(log.since(v0 + 2, 501).isEmpty());
        assertEquals(1, log.since(500, 501).orElseThrow().size());
        // a text that is not the text of the version
        long version = document.getVersion();
        type(document, 0, "a");
        assertInstanceOf(TextDocument.Rebased.Unknown.class,
            document.rebase(List.of(TextEdit.insert(0, "b")), version, "unrelated"));
    }

    private static void type(TextDocument d, int offset, String s) {
        assertTrue(d.applyEdits(List.of(TextEdit.insert(offset, s)), TYPING));
    }
}
