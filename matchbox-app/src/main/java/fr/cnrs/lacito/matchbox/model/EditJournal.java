package fr.cnrs.lacito.matchbox.model;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * Undo and redo journal of a whole project: the changes of all its documents, in the order they
 * were made, whatever made them (typing, views, assistant, search) and whether or not the
 * document is displayed. Undo reverts the last change made anywhere.
 *
 * <p>Consecutive typing changes in one document that are contiguous and less than
 * {@value #MERGE_DELAY_MS} ms apart form one entry; other changes are one entry each (one
 * validated view edit = one entry). The changes made during {@link #grouped} form one entry,
 * possibly spanning several documents.
 *
 * <p>Undoing in reverse order keeps the entries consistent: when an entry is undone, every later
 * change of its documents has been undone already, so its inverse edits apply to the text they
 * were computed against; changes of other documents do not interfere. The entries of a document
 * that leaves the project are dropped. Used on one thread (the JavaFX thread in the GUI).
 */
public final class EditJournal {

    static final long MERGE_DELAY_MS = 500;
    /** Entries kept for undo; the oldest are dropped first. */
    static final int MAX_ENTRIES = 1000;

    /** The changes of one document in an entry, in the order they were applied. */
    public record Part(TextDocument document, List<DocumentChange> changes) {
        public Part {
            changes = List.copyOf(changes);
        }

        /** Offset of the first character changed by the first change. */
        public int firstOffset() {
            return changes.get(0).firstOffset();
        }
    }

    /** One undoable step: the changes of one or several documents. */
    public record Entry(List<Part> parts, String description, long lastTimeMs) {
        public Entry {
            parts = List.copyOf(parts);
        }

        public List<TextDocument> documents() {
            return parts.stream().map(Part::document).toList();
        }

        /** "Edit Translation (notes.txt)", "Rename tag (3 files)". */
        public String label() {
            return description + " (" + where() + ")";
        }

        /** "notes.txt" or "3 files". */
        public String where() {
            if (parts.size() == 1) {
                Path name = parts.get(0).document().getFilePath().getFileName();
                return name == null ? parts.get(0).document().getFilePath().toString() : name.toString();
            }
            return parts.size() + " files";
        }
    }

    private final LongSupplier clockMs;
    private final Deque<Entry> undo = new ArrayDeque<>();
    private final Deque<Entry> redo = new ArrayDeque<>();
    private final Map<TextDocument, DocumentListener> tracked = new IdentityHashMap<>();
    private final ReadOnlyBooleanWrapper undoAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper redoAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper undoLabel = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper redoLabel = new ReadOnlyStringWrapper("");
    private boolean preventMerge;
    /** Changes recorded during {@link #grouped}, by document; null outside a group. */
    private Map<TextDocument, List<DocumentChange>> group;

    public EditJournal() {
        this(System::currentTimeMillis);
    }

    EditJournal(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    // ------------------------------------------------------------------
    // Documents
    // ------------------------------------------------------------------

    /** Records the changes of a document (when it enters the project). */
    public void track(TextDocument document) {
        tracked.computeIfAbsent(document, d -> {
            DocumentListener listener = this::record;
            d.addChangeListener(listener);
            return listener;
        });
    }

    /** Stops recording a document and drops its changes (when it leaves the project). */
    public void untrack(TextDocument document) {
        DocumentListener listener = tracked.remove(document);
        if (listener == null) return;
        document.removeChangeListener(listener);
        dropDocument(undo, document);
        dropDocument(redo, document);
        updateProperties();
    }

    private static void dropDocument(Deque<Entry> entries, TextDocument document) {
        List<Entry> kept = new ArrayList<>();
        for (Entry e : entries) {
            List<Part> parts = e.parts().stream().filter(p -> p.document() != document).toList();
            if (!parts.isEmpty()) {
                kept.add(parts.size() == e.parts().size() ? e : new Entry(parts, e.description(), e.lastTimeMs()));
            }
        }
        entries.clear();
        entries.addAll(kept);
    }

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    private void record(DocumentChange change) {
        EditOrigin.Kind kind = change.origin().kind();
        if (kind == EditOrigin.Kind.UNDO || kind == EditOrigin.Kind.REDO) {
            return;
        }
        redo.clear();
        if (group != null) {
            group.computeIfAbsent(change.document(), d -> new ArrayList<>()).add(change);
            updateProperties();
            return;
        }
        long now = clockMs.getAsLong();
        Entry last = undo.peek();
        if (!preventMerge && last != null && mergeable(last, change, now)) {
            Part part = last.parts().get(0);
            List<DocumentChange> changes = new ArrayList<>(part.changes());
            changes.add(change);
            undo.pop();
            undo.push(new Entry(List.of(new Part(part.document(), changes)), last.description(), now));
        } else {
            push(new Entry(List.of(new Part(change.document(), List.of(change))), change.origin().describe(), now));
        }
        preventMerge = !change.origin().isTyping();
        updateProperties();
    }

    private void push(Entry entry) {
        undo.push(entry);
        while (undo.size() > MAX_ENTRIES) {
            undo.removeLast();
        }
    }

    private static boolean mergeable(Entry last, DocumentChange change, long now) {
        if (last.parts().size() != 1 || last.parts().get(0).document() != change.document()) return false;
        List<DocumentChange> changes = last.parts().get(0).changes();
        DocumentChange previous = changes.get(changes.size() - 1);
        if (!previous.origin().isTyping() || !change.origin().isTyping()) return false;
        if (now - last.lastTimeMs() > MERGE_DELAY_MS) return false;
        if (previous.edits().size() != 1 || change.edits().size() != 1) return false;
        var p = previous.edits().get(0);
        var c = change.edits().get(0);
        // typing forward, or deleting backward/forward from the same place
        return c.start() == p.start() + p.replacement().length() || c.end() == p.start() || c.start() == p.start();
    }

    /** The next change starts a new entry (e.g. after the caret jumped). */
    public void preventMerge() {
        preventMerge = true;
    }

    /**
     * Runs {@code changes}: the changes they make, in any document, form one entry described by
     * {@code description} (e.g. a tag renamed in several files). Nested calls join the outer group.
     */
    public void grouped(String description, Runnable changes) {
        if (group != null) {
            changes.run();
            return;
        }
        group = new LinkedHashMap<>();
        Map<TextDocument, List<DocumentChange>> recorded;
        try {
            changes.run();
        } finally {
            recorded = group;
            group = null;
        }
        if (!recorded.isEmpty()) {
            List<Part> parts = new ArrayList<>();
            recorded.forEach((document, list) -> parts.add(new Part(document, list)));
            push(new Entry(parts, description, clockMs.getAsLong()));
        }
        preventMerge = true;
        updateProperties();
    }

    // ------------------------------------------------------------------
    // Undo and redo
    // ------------------------------------------------------------------

    /** Undoes the last entry; empty if there is none or it could not be undone. */
    public Optional<Entry> undo() {
        Entry entry = undo.peek();
        if (entry == null) return Optional.empty();
        List<Part> parts = entry.parts();
        for (int p = parts.size() - 1; p >= 0; p--) {
            List<DocumentChange> changes = parts.get(p).changes();
            for (int i = changes.size() - 1; i >= 0; i--) {
                if (!parts.get(p).document().applyEdits(changes.get(i).inverseEdits(),
                        EditOrigin.of(EditOrigin.Kind.UNDO, this))) {
                    clear(); // the journal no longer matches the texts
                    return Optional.empty();
                }
            }
        }
        redo.push(undo.pop());
        preventMerge = true;
        updateProperties();
        return Optional.of(entry);
    }

    /** Applies again the last undone entry; empty if there is none or it could not be applied. */
    public Optional<Entry> redo() {
        Entry entry = redo.peek();
        if (entry == null) return Optional.empty();
        for (Part part : entry.parts()) {
            for (DocumentChange change : part.changes()) {
                if (!part.document().applyEdits(change.edits(), EditOrigin.of(EditOrigin.Kind.REDO, this))) {
                    clear();
                    return Optional.empty();
                }
            }
        }
        undo.push(redo.pop());
        preventMerge = true;
        updateProperties();
        return Optional.of(entry);
    }

    public void clear() {
        undo.clear();
        redo.clear();
        updateProperties();
    }

    /** The entry {@link #undo()} would revert. */
    public Optional<Entry> nextUndo() {
        return Optional.ofNullable(undo.peek());
    }

    /** The entry {@link #redo()} would apply. */
    public Optional<Entry> nextRedo() {
        return Optional.ofNullable(redo.peek());
    }

    /** Number of entries that can be undone. */
    public int size() {
        return undo.size();
    }

    private void updateProperties() {
        undoAvailable.set(!undo.isEmpty());
        redoAvailable.set(!redo.isEmpty());
        undoLabel.set(undo.isEmpty() ? "" : undo.peek().label());
        redoLabel.set(redo.isEmpty() ? "" : redo.peek().label());
    }

    public ReadOnlyBooleanProperty undoAvailableProperty() {
        return undoAvailable.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty redoAvailableProperty() {
        return redoAvailable.getReadOnlyProperty();
    }

    /** Label of the next entry to undo ("Edit Translation (notes.txt)"); empty if none. */
    public ReadOnlyStringProperty undoLabelProperty() {
        return undoLabel.getReadOnlyProperty();
    }

    /** Label of the next entry to redo; empty if none. */
    public ReadOnlyStringProperty redoLabelProperty() {
        return redoLabel.getReadOnlyProperty();
    }

    /** True if the document's changes are recorded. */
    boolean isTracked(TextDocument document) {
        return tracked.containsKey(document);
    }
}
