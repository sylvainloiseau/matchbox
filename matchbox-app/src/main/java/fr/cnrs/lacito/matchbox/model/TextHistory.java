package fr.cnrs.lacito.matchbox.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * Undo and redo history of one {@link TextDocument}, fed by its changes, whatever made them
 * (typing, views, assistant, search) and whether or not the document is displayed.
 *
 * <p>Consecutive typing changes that are contiguous and less than {@value #MERGE_DELAY_MS} ms apart
 * form one step; other changes are one step each (one validated view edit = one step).
 */
public final class TextHistory {

    static final long MERGE_DELAY_MS = 500;

    /** One undo step: changes in the order they were applied. */
    private record Step(List<DocumentChange> changes, long lastTimeMs) {}

    private final TextDocument document;
    private final LongSupplier clockMs;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper undoAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper redoAvailable = new ReadOnlyBooleanWrapper(false);
    private final DocumentListener listener = this::record;
    private boolean preventMerge;

    public TextHistory(TextDocument document) {
        this(document, System::currentTimeMillis);
    }

    TextHistory(TextDocument document, LongSupplier clockMs) {
        this.document = document;
        this.clockMs = clockMs;
        document.addChangeListener(listener);
    }

    /** Stops recording (when the document leaves the project). */
    public void dispose() {
        document.removeChangeListener(listener);
    }

    private void record(DocumentChange change) {
        EditOrigin.Kind kind = change.origin().kind();
        if (kind == EditOrigin.Kind.UNDO || kind == EditOrigin.Kind.REDO) {
            return;
        }
        redo.clear();
        long now = clockMs.getAsLong();
        Step last = undo.peek();
        if (!preventMerge && last != null && mergeable(last, change, now)) {
            List<DocumentChange> changes = new ArrayList<>(last.changes());
            changes.add(change);
            undo.pop();
            undo.push(new Step(changes, now));
        } else {
            undo.push(new Step(List.of(change), now));
        }
        preventMerge = !change.origin().isTyping();
        updateProperties();
    }

    private static boolean mergeable(Step last, DocumentChange change, long now) {
        DocumentChange previous = last.changes().get(last.changes().size() - 1);
        if (!previous.origin().isTyping() || !change.origin().isTyping()) return false;
        if (now - last.lastTimeMs() > MERGE_DELAY_MS) return false;
        if (previous.edits().size() != 1 || change.edits().size() != 1) return false;
        var p = previous.edits().get(0);
        var c = change.edits().get(0);
        // typing forward, or deleting backward/forward from the same place
        return c.start() == p.start() + p.replacement().length() || c.end() == p.start() || c.start() == p.start();
    }

    /** The next change starts a new step. */
    public void preventMerge() {
        preventMerge = true;
    }

    /** Undoes the last step. */
    public boolean undo() {
        Step step = undo.peek();
        if (step == null) return false;
        List<DocumentChange> changes = step.changes();
        for (int i = changes.size() - 1; i >= 0; i--) {
            if (!document.applyEdits(changes.get(i).inverseEdits(), EditOrigin.of(EditOrigin.Kind.UNDO, this))) {
                clear(); // the history no longer matches the text
                return false;
            }
        }
        redo.push(undo.pop());
        preventMerge = true;
        updateProperties();
        return true;
    }

    /** Applies again the last undone step. */
    public boolean redo() {
        Step step = redo.peek();
        if (step == null) return false;
        for (DocumentChange change : step.changes()) {
            if (!document.applyEdits(change.edits(), EditOrigin.of(EditOrigin.Kind.REDO, this))) {
                clear();
                return false;
            }
        }
        undo.push(redo.pop());
        preventMerge = true;
        updateProperties();
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
        updateProperties();
    }

    private void updateProperties() {
        undoAvailable.set(!undo.isEmpty());
        redoAvailable.set(!redo.isEmpty());
    }

    public ReadOnlyBooleanProperty undoAvailableProperty() {
        return undoAvailable.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty redoAvailableProperty() {
        return redoAvailable.getReadOnlyProperty();
    }
}
