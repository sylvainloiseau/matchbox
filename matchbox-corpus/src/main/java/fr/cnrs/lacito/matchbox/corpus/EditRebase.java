package fr.cnrs.lacito.matchbox.corpus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Moves edits and offsets computed against one state of a text through the changes made since,
 * by position only: the text is never searched.
 *
 * <p>A <em>change</em> is a sorted list of non-overlapping {@link TextEdit}s, all relative to the
 * text before the change (as a document records them). For an edit E = [s, e) and an edit C =
 * [cs, ce) of a change:
 * <ul>
 *   <li>C ends at or before s (an insertion at s included): E is shifted by the change of length
 *       of C;</li>
 *   <li>C starts at or after e: E is unchanged (an insertion at e does not touch E);</li>
 *   <li>otherwise C replaces characters of E or inserts strictly inside it: a conflict.</li>
 * </ul>
 * An insertion E and an insertion C at the same offset: E goes after what C inserted. Moving is
 * monotonic, so edits that did not overlap still do not overlap.
 *
 * <p>Pure and stateless.
 */
public final class EditRebase {

    private EditRebase() {}

    /** Where an offset goes when text is inserted exactly at it, or when its character is replaced. */
    public enum Bias {
        /** Stays before the inserted text; at the start of a replaced range. */
        BEFORE,
        /** Goes after the inserted text; at the end of the replacement of a replaced range. */
        AFTER
    }

    /** Result of {@link #rebase}. */
    public sealed interface Result {
        /** The edits, relative to the text after the changes, in the order given. */
        record Moved(List<TextEdit> edits) implements Result {
            public Moved {
                edits = List.copyOf(edits);
            }
        }

        /**
         * {@code edit} (as given) met {@code change}, an edit of the changes relative to the text
         * the changes applied to at that point.
         */
        record Conflict(TextEdit edit, TextEdit change) implements Result {}
    }

    /**
     * Moves {@code edits} (relative to the text before the first change) through {@code changes},
     * in order.
     */
    public static Result rebase(List<TextEdit> edits, List<List<TextEdit>> changes) {
        List<TextEdit> moved = new ArrayList<>(edits);
        for (List<TextEdit> change : changes) {
            for (int i = 0; i < moved.size(); i++) {
                TextEdit e = moved.get(i);
                int delta = 0;
                for (TextEdit c : change) {
                    if (c.end() <= e.start()) {
                        delta += c.delta();
                    } else if (c.start() >= e.end()) {
                        break;
                    } else {
                        return new Result.Conflict(edits.get(i), c);
                    }
                }
                moved.set(i, delta == 0 ? e : e.shifted(delta));
            }
        }
        return new Result.Moved(moved);
    }

    /**
     * An offset (a position between characters) moved through {@code changes}. An offset inside a
     * replaced range goes to its start ({@link Bias#BEFORE}) or to the end of its replacement
     * ({@link Bias#AFTER}); an insertion exactly at the offset moves it with {@code AFTER} only.
     */
    public static int offset(int offset, Bias bias, List<List<TextEdit>> changes) {
        int result = offset;
        for (List<TextEdit> change : changes) {
            int delta = 0;
            int moved = result;
            for (TextEdit c : change) {
                if (c.end() < result || c.end() == result && c.start() < result) {
                    delta += c.delta();
                } else if (c.start() == result && c.end() == result) {
                    if (bias == Bias.AFTER) delta += c.delta();
                } else if (c.start() >= result) {
                    break;
                } else {
                    // inside the replaced range
                    moved = bias == Bias.AFTER ? c.start() + delta + c.replacement().length() : c.start() + delta;
                    delta = Integer.MIN_VALUE;
                    break;
                }
            }
            result = delta == Integer.MIN_VALUE ? moved : result + delta;
        }
        return result;
    }

    /**
     * The net change of {@code changes}, relative to {@code original} (the text before the first
     * change): edits of successive changes that overlap or touch are merged, each merged edit is
     * reduced to its minimal form (common prefix and suffix removed), and edits that leave the text
     * as it was (typing a character, then deleting it) are dropped. The result is sorted and
     * non-overlapping, and {@code TextEdit.applyAll(original, result)} gives the text after the
     * changes.
     *
     * @throws IllegalArgumentException if a change does not match the text it applies to
     */
    public static List<TextEdit> compose(String original, List<List<TextEdit>> changes) {
        // net edits relative to original, sorted, non-overlapping; replacement = current text
        List<TextEdit> net = new ArrayList<>();
        for (List<TextEdit> change : changes) {
            // from the end, so that the current positions of the lower edits stay valid
            List<TextEdit> descending = change.stream()
                .sorted(Comparator.comparingInt(TextEdit::start).reversed()).toList();
            for (TextEdit c : descending) {
                merge(original, net, c);
            }
        }
        List<TextEdit> result = new ArrayList<>();
        for (TextEdit n : net) {
            TextEdit minimal = minimal(n);
            if (minimal != null) result.add(minimal);
        }
        return List.copyOf(result);
    }

    /** Merges {@code c} (current coordinates) into {@code net} (original coordinates). */
    private static void merge(String original, List<TextEdit> net, TextEdit c) {
        // current range of each net edit: [start + before, start + before + replacement length)
        int before = 0;
        int first = -1;
        int last = -1;
        int beforeFirst = 0;
        for (int i = 0; i < net.size(); i++) {
            TextEdit n = net.get(i);
            int curStart = n.start() + before;
            int curEnd = curStart + n.replacement().length();
            if (curEnd >= c.start() && curStart <= c.end()) {
                if (first < 0) {
                    first = i;
                    beforeFirst = before;
                }
                last = i;
            } else if (curStart > c.end()) {
                break;
            }
            before += n.delta();
            if (first < 0) beforeFirst = before;
        }
        int curStart;
        int curEnd;
        int origStart;
        int origEnd;
        StringBuilder current = new StringBuilder();
        if (first < 0) {
            curStart = c.start();
            curEnd = c.end();
            origStart = c.start() - beforeFirst;
            origEnd = c.end() - beforeFirst;
            current.append(original, origStart, origEnd);
        } else {
            TextEdit f = net.get(first);
            int fCur = f.start() + beforeFirst;
            curStart = Math.min(c.start(), fCur);
            origStart = curStart == fCur ? f.start() : c.start() - beforeFirst;
            // pieces from curStart to the end of the last merged net edit or of c
            int origPos = origStart;
            int curPos = curStart;
            if (curStart < fCur) {
                current.append(original, origStart, f.start());
                curPos = fCur;
                origPos = f.start();
            }
            int run = beforeFirst;
            for (int i = first; i <= last; i++) {
                TextEdit n = net.get(i);
                if (n.start() > origPos) {
                    current.append(original, origPos, n.start());
                    curPos += n.start() - origPos;
                }
                current.append(n.replacement());
                curPos += n.replacement().length();
                origPos = n.end();
                run += n.delta();
            }
            if (c.end() > curPos) {
                origEnd = c.end() - run;
                current.append(original, origPos, origEnd);
                curPos = c.end();
            } else {
                origEnd = origPos;
            }
            curEnd = curPos;
        }
        int from = c.start() - curStart;
        int to = c.end() - curStart;
        if (!current.substring(from, to).equals(c.expectedOriginal())) {
            throw new IllegalArgumentException("The change does not match the text at " + c.start());
        }
        current.replace(from, to, c.replacement());
        TextEdit merged = new TextEdit(origStart, origEnd, original.substring(origStart, origEnd), current.toString());
        if (first < 0) {
            int at = 0;
            while (at < net.size() && net.get(at).start() < origStart) at++;
            net.add(at, merged);
        } else {
            for (int i = last; i >= first; i--) net.remove(i);
            net.add(first, merged);
        }
        assert curEnd >= curStart;
    }

    /** {@code e} without the common prefix and suffix of its original and replacement; null if equal. */
    private static TextEdit minimal(TextEdit e) {
        String a = e.expectedOriginal();
        String b = e.replacement();
        if (a.equals(b)) return null;
        int prefix = 0;
        int max = Math.min(a.length(), b.length());
        while (prefix < max && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < max - prefix && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) suffix++;
        return new TextEdit(e.start() + prefix, e.end() - suffix,
            a.substring(prefix, a.length() - suffix), b.substring(prefix, b.length() - suffix));
    }
}
