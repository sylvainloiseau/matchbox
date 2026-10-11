package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.Optional;

/**
 * Duplicating, deleting and moving the lines of a selection (the lines it touches, the caret's
 * line if it is empty). Each command is one {@link TextEdit} and says where the selection goes.
 */
public final class LineCommands {

    /**
     * A command's result.
     *
     * @param edit           the change of the text
     * @param selectionStart selection after the change
     * @param selectionEnd   end of the selection after the change (exclusive)
     */
    public record Result(TextEdit edit, int selectionStart, int selectionEnd) {}

    private LineCommands() {
    }

    /** First and last line of a selection; a selection ending at the start of a line does not include it. */
    private static int[] lines(LineIndex index, int start, int end) {
        int first = index.lineOf(start);
        int last = index.lineOf(end);
        if (end > start && last > first && index.start(last) == end) last--;
        return new int[] {first, last};
    }

    /** Copies the lines below them; the copy is selected. */
    public static Result duplicate(String text, int start, int end) {
        LineIndex index = new LineIndex(text);
        int[] l = lines(index, start, end);
        int from = index.start(l[0]);
        int to = index.end(l[1]);
        String block = text.substring(from, to);
        TextEdit edit = new TextEdit(to, to, "", "\n" + block);
        int shift = to - from + 1;
        return new Result(edit, start + shift, end + shift);
    }

    /**
     * Removes the lines with their newline; the caret goes to the start of the line that follows,
     * or of the line above when the last lines are removed.
     */
    public static Result delete(String text, int start, int end) {
        LineIndex index = new LineIndex(text);
        int[] l = lines(index, start, end);
        int from = index.start(l[0]);
        int to = index.end(l[1]);
        int caret;
        if (l[1] + 1 < index.lineCount()) {
            to++;
            caret = from;
        } else if (l[0] > 0) {
            // the last lines: the newline before them goes
            from--;
            caret = index.start(l[0] - 1);
        } else {
            caret = 0;
        }
        return new Result(new TextEdit(from, to, text.substring(from, to), ""), caret, caret);
    }

    /** Swaps the lines with the line above; empty if they are the first lines. */
    public static Optional<Result> moveUp(String text, int start, int end) {
        LineIndex index = new LineIndex(text);
        int[] l = lines(index, start, end);
        if (l[0] == 0) return Optional.empty();
        int from = index.start(l[0] - 1);
        int to = index.end(l[1]);
        String above = index.line(l[0] - 1);
        String block = text.substring(index.start(l[0]), to);
        TextEdit edit = new TextEdit(from, to, text.substring(from, to), block + "\n" + above);
        int shift = above.length() + 1;
        return Optional.of(new Result(edit, start - shift, end - shift));
    }

    /** Swaps the lines with the line below; empty if they are the last lines. */
    public static Optional<Result> moveDown(String text, int start, int end) {
        LineIndex index = new LineIndex(text);
        int[] l = lines(index, start, end);
        if (l[1] + 1 >= index.lineCount()) return Optional.empty();
        int from = index.start(l[0]);
        int to = index.end(l[1] + 1);
        String below = index.line(l[1] + 1);
        String block = text.substring(from, index.end(l[1]));
        TextEdit edit = new TextEdit(from, to, text.substring(from, to), below + "\n" + block);
        int shift = below.length() + 1;
        return Optional.of(new Result(edit, start + shift, end + shift));
    }
}
