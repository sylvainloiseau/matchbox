package fr.cnrs.lacito.matchbox.assist.suggestion;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds where an edit proposed by a model applies. Models give unreliable character offsets, so
 * they return the exact text to replace, which is searched for in the reviewed range.
 */
public final class EditLocator {

    private EditLocator() {}

    /**
     * The edit replacing {@code original} by {@code replacement} in {@code text[scopeStart, scopeEnd)}.
     * The original must occur exactly once in the scope, or, if {@code preferredOffset >= 0}, one
     * occurrence must be strictly nearer to it than the others. Empty if the original is empty,
     * absent, ambiguous, or equal to the replacement.
     */
    public static Optional<TextEdit> locate(String text, int scopeStart, int scopeEnd, String original,
                                            String replacement, int preferredOffset) {
        if (original == null || original.isEmpty() || replacement == null || original.equals(replacement)) {
            return Optional.empty();
        }
        int from = Math.max(0, scopeStart);
        int to = Math.min(text.length(), scopeEnd);
        List<Integer> found = new ArrayList<>();
        for (int i = text.indexOf(original, from); i >= 0 && i + original.length() <= to; i = text.indexOf(original, i + 1)) {
            found.add(i);
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        int chosen;
        if (found.size() == 1) {
            chosen = found.get(0);
        } else if (preferredOffset >= 0) {
            found.sort((a, b) -> Integer.compare(distance(a, original, preferredOffset), distance(b, original, preferredOffset)));
            if (distance(found.get(0), original, preferredOffset) == distance(found.get(1), original, preferredOffset)) {
                return Optional.empty();
            }
            chosen = found.get(0);
        } else {
            return Optional.empty();
        }
        return Optional.of(new TextEdit(chosen, chosen + original.length(), original, replacement));
    }

    /** Distance from an offset to the range of an occurrence (0 if inside). */
    private static int distance(int start, String original, int offset) {
        int end = start + original.length();
        if (offset < start) return start - offset;
        if (offset > end) return offset - end;
        return 0;
    }
}
