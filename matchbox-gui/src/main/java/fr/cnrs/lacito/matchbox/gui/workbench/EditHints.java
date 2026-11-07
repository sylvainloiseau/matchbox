package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Where the last changes were made, so that the modes keep the edited object selected after the
 * text is parsed again: the edit service records the offset of each view edit, a text action the
 * object to select after the next parse. Read by the {@link SelectionTracker}s.
 *
 * <p>Each offset is kept with the version of the document it refers to, and moved to the version
 * of the parse that reads it (the user may type between the edit and the parse).
 */
public final class EditHints {

    /** Moves an offset of a text from one version of its document to another; empty if unknown. */
    @FunctionalInterface
    public interface OffsetMover {
        OptionalInt move(TextId text, int offset, long from, long to);

        /** Offsets kept as they are. */
        OffsetMover NONE = (text, offset, from, to) -> OptionalInt.of(offset);
    }

    private record Hint(int offset, long version) {}

    private final OffsetMover mover;
    private final Map<TextId, Hint> lastEdit = new HashMap<>();
    private final Map<TextId, Hint> pendingSelection = new HashMap<>();

    public EditHints() {
        this(OffsetMover.NONE);
    }

    public EditHints(OffsetMover mover) {
        this.mover = mover;
    }

    /** A view changed {@code text} at {@code offset}, an offset of an unknown version. */
    public void lastEdit(TextId text, int offset) {
        lastEdit(text, offset, Text.UNKNOWN_VERSION);
    }

    /** A view changed {@code text} at {@code offset} of version {@code version} of its document. */
    public void lastEdit(TextId text, int offset, long version) {
        lastEdit.put(text, new Hint(offset, version));
    }

    /** Offset of the last view edit of a text, as recorded. */
    public OptionalInt lastEditOffset(TextId text) {
        Hint hint = lastEdit.get(text);
        return hint == null ? OptionalInt.empty() : OptionalInt.of(hint.offset());
    }

    /** Offset of the last view edit of a text, in the content of {@code text}. */
    public OptionalInt lastEditOffset(Text text) {
        Hint hint = lastEdit.get(text.id());
        return hint == null ? OptionalInt.empty() : moved(text, hint);
    }

    /** After the next parse of {@code text}, the object at {@code offset} should be selected. */
    public void selectAfterParse(TextId text, int offset) {
        selectAfterParse(text, offset, Text.UNKNOWN_VERSION);
    }

    /** Same as {@link #selectAfterParse(TextId, int)}, {@code offset} being of version {@code version}. */
    public void selectAfterParse(TextId text, int offset, long version) {
        pendingSelection.put(text, new Hint(offset, version));
    }

    /** The pending selection of a text, removed (the first mode asking gets it). */
    public OptionalInt takePendingSelection(TextId text) {
        Hint hint = pendingSelection.remove(text);
        return hint == null ? OptionalInt.empty() : OptionalInt.of(hint.offset());
    }

    /** The pending selection of a text, in the content of {@code text}, removed. */
    public OptionalInt takePendingSelection(Text text) {
        Hint hint = pendingSelection.remove(text.id());
        return hint == null ? OptionalInt.empty() : moved(text, hint);
    }

    private OptionalInt moved(Text text, Hint hint) {
        if (hint.version() == Text.UNKNOWN_VERSION || text.version() == Text.UNKNOWN_VERSION
            || hint.version() == text.version()) {
            return OptionalInt.of(hint.offset());
        }
        OptionalInt moved = mover.move(text.id(), hint.offset(), hint.version(), text.version());
        // unknown history (or a parse older than the hint): the offset as recorded
        return moved.isPresent() ? moved : OptionalInt.of(hint.offset());
    }

    /** Forgets everything (the project changed). */
    public void clear() {
        lastEdit.clear();
        pendingSelection.clear();
    }
}
