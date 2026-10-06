package fr.cnrs.lacito.matchbox.model;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.List;

/**
 * One change applied to a {@link TextDocument}.
 *
 * @param document      the changed document
 * @param edits         edits relative to the text before the change, sorted, non-overlapping
 * @param origin        who made the change
 * @param versionBefore version of the document before the change
 * @param versionAfter  version after it
 */
public record DocumentChange(TextDocument document, List<TextEdit> edits, EditOrigin origin,
                             long versionBefore, long versionAfter) {

    public DocumentChange {
        edits = List.copyOf(edits);
    }

    /** Edits that undo this change, relative to the text after it (non-overlapping). */
    public List<TextEdit> inverseEdits() {
        return TextDocument.shiftedForward(edits).stream().map(TextEdit::inverse).toList();
    }

    /** Offset of the first changed character. */
    public int firstOffset() {
        return edits.isEmpty() ? 0 : edits.get(0).start();
    }
}
