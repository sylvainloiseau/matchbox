package fr.cnrs.lacito.matchbox.assist.suggestion;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.Optional;

/**
 * Last resort to place an edit in a text that changed when the changes made since the edit was
 * computed are not known (otherwise edits are moved by position with
 * {@link fr.cnrs.lacito.matchbox.corpus.EditRebase}): the original text of the edit is searched
 * near its old position, and accepted only if it occurs exactly once there.
 */
public final class Relocation {

    /** Distance around the old position where the original text is searched for. */
    static final int SEARCH_RADIUS = 200;

    private Relocation() {}

    /**
     * The edit in a text that may have changed: the only occurrence of its original text within
     * {@link #SEARCH_RADIUS} characters of its old range; empty if there is none or several, or if
     * the edit is an insertion (nothing to search for).
     */
    public static Optional<TextEdit> find(TextEdit edit, String text) {
        String original = edit.expectedOriginal();
        if (original.isEmpty()) {
            return Optional.empty();
        }
        int from = Math.max(0, edit.start() - SEARCH_RADIUS);
        int to = Math.min(text.length(), edit.end() + SEARCH_RADIUS);
        int found = -1;
        for (int i = text.indexOf(original, from); i >= 0 && i + original.length() <= to; i = text.indexOf(original, i + 1)) {
            if (found >= 0) {
                return Optional.empty();
            }
            found = i;
        }
        return found < 0 ? Optional.empty()
            : Optional.of(new TextEdit(found, found + original.length(), original, edit.replacement()));
    }
}
