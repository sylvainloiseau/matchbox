package fr.cnrs.lacito.matchbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditJournalTest {

    private static final EditOrigin TYPING = EditOrigin.of(EditOrigin.Kind.TYPING, "editor");

    @TempDir
    Path dir;
    private TextDocument a;
    private TextDocument b;
    private long now;
    private EditJournal journal;

    @BeforeEach
    void setUp() throws IOException {
        a = new TextDocument(Files.writeString(dir.resolve("a.txt"), "aaa"));
        b = new TextDocument(Files.writeString(dir.resolve("b.txt"), "bbb"));
        journal = new EditJournal(() -> now);
        journal.track(a);
        journal.track(b);
    }

    @Test
    void undoFollowsTheOrderOfChangesAcrossDocuments() {
        view(a, 0, "A", "Edit Translation");
        type(b, 3, "!");
        view(a, 3, "Z", null);

        assertEquals("Edit (a.txt)", journal.undoLabelProperty().get());
        assertTrue(journal.undo().isPresent());
        assertEquals("Aaaa", a.getCurrentText());
        assertEquals("Typing (b.txt)", journal.undoLabelProperty().get());
        assertTrue(journal.undo().isPresent());
        assertEquals("bbb", b.getCurrentText());
        assertEquals("Edit Translation (a.txt)", journal.undoLabelProperty().get());
        assertTrue(journal.undo().isPresent());
        assertEquals("aaa", a.getCurrentText());
        assertFalse(journal.undoAvailableProperty().get());

        assertEquals("Edit Translation (a.txt)", journal.redoLabelProperty().get());
        assertTrue(journal.redo().isPresent());
        assertTrue(journal.redo().isPresent());
        assertEquals("Aaaa", a.getCurrentText());
        assertEquals("bbb!", b.getCurrentText());
    }

    @Test
    void typingIsNotMergedAcrossDocuments() {
        type(a, 3, "1");
        type(b, 3, "2");
        type(a, 4, "3");
        assertEquals(3, journal.size());
    }

    @Test
    void aChangeInAnotherDocumentClearsRedo() {
        type(a, 3, "1");
        journal.undo();
        assertTrue(journal.redoAvailableProperty().get());
        type(b, 0, ">");
        assertFalse(journal.redoAvailableProperty().get());
    }

    @Test
    void entriesOfARemovedDocumentAreDropped() {
        view(a, 0, "A", null);
        view(b, 0, "B", null);
        view(a, 0, "C", null);
        journal.untrack(b);
        assertEquals(2, journal.size());
        journal.undo();
        journal.undo();
        assertEquals("aaa", a.getCurrentText());
        // b is no longer recorded
        view(b, 0, "D", null);
        assertEquals(0, journal.size());
    }

    @Test
    void groupedChangesAreOneEntry() {
        journal.grouped("Rename tag", () -> {
            view(a, 0, "A", null);
            view(b, 0, "B", null);
            view(a, 0, "C", null);
        });
        assertEquals(1, journal.size());
        assertEquals("Rename tag (2 files)", journal.undoLabelProperty().get());
        assertEquals(List.of(a, b), journal.nextUndo().orElseThrow().documents());
        journal.undo();
        assertEquals("aaa", a.getCurrentText());
        assertEquals("bbb", b.getCurrentText());
        journal.redo();
        assertEquals("CAaaa", a.getCurrentText());
        assertEquals("Bbbb", b.getCurrentText());
    }

    @Test
    void undoAfterSaveMakesTheDocumentDirty() throws IOException {
        type(a, 3, "!");
        a.writeFile();
        assertFalse(a.getCouldBeWritten());
        journal.undo();
        assertTrue(a.getCouldBeWritten());
    }

    @Test
    void theJournalIsBounded() {
        for (int i = 0; i < EditJournal.MAX_ENTRIES + 5; i++) {
            view(a, 0, "x", null);
        }
        assertEquals(EditJournal.MAX_ENTRIES, journal.size());
    }

    private void type(TextDocument d, int offset, String s) {
        d.applyEdits(List.of(TextEdit.insert(offset, s)), TYPING);
    }

    private void view(TextDocument d, int offset, String s, String description) {
        d.applyEdits(List.of(TextEdit.insert(offset, s)), EditOrigin.of(EditOrigin.Kind.VIEW, "view", description));
    }
}
