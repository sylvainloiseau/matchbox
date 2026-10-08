package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Where the last changes were made, so that the modes keep the edited object selected after the
 * text is parsed again: the edit service records the offset of each view edit, a text action the
 * object to select after the next parse. Read by the {@link SelectionTracker}s.
 */
public final class EditHints {

    private final Map<TextId, Integer> lastEdit = new HashMap<>();
    private final Map<TextId, Integer> pendingSelection = new HashMap<>();

    /** A view changed {@code text} at {@code offset}. */
    public void lastEdit(TextId text, int offset) {
        lastEdit.put(text, offset);
    }

    /** Offset of the last view edit of a text. */
    public OptionalInt lastEditOffset(TextId text) {
        Integer offset = lastEdit.get(text);
        return offset == null ? OptionalInt.empty() : OptionalInt.of(offset);
    }

    /** After the next parse of {@code text}, the object at {@code offset} should be selected. */
    public void selectAfterParse(TextId text, int offset) {
        pendingSelection.put(text, offset);
    }

    /** The pending selection of a text, removed (the first mode asking gets it). */
    public OptionalInt takePendingSelection(TextId text) {
        Integer offset = pendingSelection.remove(text);
        return offset == null ? OptionalInt.empty() : OptionalInt.of(offset);
    }

    /** Forgets everything (the project changed). */
    public void clear() {
        lastEdit.clear();
        pendingSelection.clear();
    }
}
