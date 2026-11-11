package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearTimeline;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits of the words of an IGT, whatever its notation (words are separated by whitespace, the spans
 * of the capability locate them): insert or delete a word in every segmented line (both levels:
 * word n is the same word everywhere), align the words in columns. Orthographic lines and
 * translations are not touched. Pure: edits relative to the parsed content.
 */
public final class InterlinearEdits {

    /** Placeholder of an inserted word ({@code _} is Leipzig rule 4A; {@code ?} reads as "unknown"). */
    public static final String PLACEHOLDER = "?";

    private InterlinearEdits() {}

    private static List<LineReading> segmented(InterlinearReading r) {
        return r.lines().stream().filter(LineReading::isSegmented).toList();
    }

    /** Inserts a word before word {@code index} ({@code index} = number of words: at the end). */
    public static List<TextEdit> insertWord(InterlinearReading r, int index) {
        List<TextEdit> edits = new ArrayList<>();
        for (LineReading l : segmented(r)) {
            List<Word> words = l.words();
            String value = PLACEHOLDER;
            if (l.line() instanceof InterlinearTimeline t && !t.values().isEmpty()) {
                List<Long> at = t.values().get(Math.min(index, t.values().size() - 1));
                value = String.valueOf(at.isEmpty() ? 0 : Math.max(0, at.get(0)));
            }
            if (words.isEmpty()) {
                edits.add(TextEdit.insert(l.line().getTextCoordinate().startIndex(), value));
            } else if (index < words.size()) {
                edits.add(TextEdit.insert(words.get(index).span().startIndex(), value + " "));
            } else {
                edits.add(TextEdit.insert(words.get(words.size() - 1).span().stopIndex() + 1, " " + value));
            }
        }
        return edits;
    }

    /** Deletes word {@code index} of every segmented line that has it, with the space after it (before it for the last). */
    public static List<TextEdit> deleteWord(InterlinearReading r, int index, String content) {
        List<TextEdit> edits = new ArrayList<>();
        for (LineReading l : segmented(r)) {
            List<Word> words = l.words();
            if (index >= words.size()) continue;
            int from = words.get(index).span().startIndex();
            int to = words.get(index).span().stopIndex() + 1;
            if (index + 1 < words.size()) {
                to = words.get(index + 1).span().startIndex();
            } else if (index > 0) {
                from = words.get(index - 1).span().stopIndex() + 1;
            }
            edits.add(TextEdit.replace(content, from, to, ""));
        }
        return edits;
    }

    /**
     * Aligns the words of the segmented lines in columns (the width of a column is the widest word of
     * any line, {@link DisplayWidth}); the indentation of each line is kept. Empty if already aligned.
     */
    public static List<TextEdit> format(InterlinearReading r, String content) {
        List<LineReading> lines = segmented(r);
        int columns = lines.stream().mapToInt(l -> l.words().size()).max().orElse(0);
        int[] width = new int[columns];
        for (LineReading l : lines) {
            List<Word> words = l.words();
            for (int w = 0; w < words.size(); w++) width[w] = Math.max(width[w], DisplayWidth.of(words.get(w).text()));
        }
        List<TextEdit> edits = new ArrayList<>();
        for (LineReading l : lines) {
            List<Word> words = l.words();
            if (words.isEmpty()) continue;
            StringBuilder s = new StringBuilder();
            for (int w = 0; w < words.size(); w++) {
                String text = words.get(w).text();
                s.append(text);
                if (w + 1 < words.size()) s.append(" ".repeat(width[w] - DisplayWidth.of(text) + 1));
            }
            int from = words.get(0).span().startIndex();
            int to = words.get(words.size() - 1).span().stopIndex() + 1;
            if (!content.substring(from, to).equals(s.toString())) {
                edits.add(TextEdit.replace(content, from, to, s.toString()));
            }
        }
        return edits;
    }

}
