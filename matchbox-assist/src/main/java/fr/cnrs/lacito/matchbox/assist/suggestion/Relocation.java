package fr.cnrs.lacito.matchbox.assist.suggestion;

import java.util.Optional;

/**
 * Keeps ranges of a text valid while the text is edited: a range is shifted when the change is
 * before it, kept when the change is after it, and searched for again (by its original text, near
 * its old position) when the change overlaps it.
 */
public final class Relocation {

    /** Distance around the old position where an overlapped range is searched for. */
    static final int SEARCH_RADIUS = 200;

    private Relocation() {}

    /**
     * The edit after a change of the text, or empty if it cannot be found any more.
     *
     * @param position offset of the change
     * @param removed  number of characters removed at {@code position}
     * @param inserted number of characters inserted at {@code position}
     * @param newText  the text after the change
     */
    public static Optional<TextEdit> relocate(TextEdit edit, int position, int removed, int inserted, String newText) {
        int delta = inserted - removed;
        if (position + removed <= edit.start()) {
            // change before the range (an insertion at its start moves it)
            TextEdit shifted = edit.shifted(delta);
            return shifted.matches(newText) ? Optional.of(shifted) : search(edit, newText);
        }
        if (position >= edit.end()) {
            // change after the range
            return edit.matches(newText) ? Optional.of(edit) : search(edit, newText);
        }
        return search(edit, newText);
    }

    /** The edit in a text that may have changed: as is if it still applies, else searched for nearby. */
    public static Optional<TextEdit> find(TextEdit edit, String text) {
        return edit.matches(text) ? Optional.of(edit) : search(edit, text);
    }

    /** The occurrence of the original text nearest to the old position, within the search radius. */
    static Optional<TextEdit> search(TextEdit edit, String text) {
        String original = edit.expectedOriginal();
        if (original.isEmpty()) {
            return Optional.empty();
        }
        int from = Math.max(0, edit.start() - SEARCH_RADIUS);
        int to = Math.min(text.length(), edit.end() + SEARCH_RADIUS);
        int best = -1;
        for (int i = text.indexOf(original, from); i >= 0 && i + original.length() <= to; i = text.indexOf(original, i + 1)) {
            if (best < 0 || Math.abs(i - edit.start()) < Math.abs(best - edit.start())) {
                best = i;
            }
        }
        return best < 0 ? Optional.empty()
            : Optional.of(new TextEdit(best, best + original.length(), original, edit.replacement()));
    }
}
