package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.MatchConflict;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The rows of the Overlaps tab: every reading of the project that meets a reading of another
 * grammar (see {@code MatchRole}), files in {@code FileOrder}, then by line. Pure.
 */
public final class OverlapRows {

    /**
     * One overlap.
     *
     * @param file     the text
     * @param fileName its name, as shown
     * @param line     first line of the reported reading (1-based)
     * @param outcome  "Not kept" or "Kept"
     * @param reading  the reading of the later grammar: kind (grammar), lines
     * @param other    the reading it meets: kind (grammar), lines
     * @param conflict the conflict
     */
    public record Row(TextId file, String fileName, int line, String outcome, String reading, String other,
                      MatchConflict conflict) {

        /** The object to select for "Show both": the reading kept, i.e. the other one if this one was dropped. */
        public CorpusObject selected() {
            return conflict.isDropped() ? conflict.other() : conflict.object();
        }

        /** The span to outline for "Show both": the reading not selected. */
        public TextCoordinate outlined() {
            return (conflict.isDropped() ? conflict.object() : conflict.other()).getTextCoordinate();
        }
    }

    private OverlapRows() {}

    /**
     * @param grammarName the name shown for a grammar id
     */
    public static List<Row> of(CorpusView corpus, Function<String, String> grammarName) {
        List<Row> rows = new ArrayList<>();
        // texts() are in FileOrder
        for (Text text : corpus.texts()) {
            List<MatchConflict> conflicts = new ArrayList<>(text.conflicts());
            conflicts.sort(Comparator.comparingInt(c -> c.object().getTextCoordinate().startIndex()));
            for (MatchConflict c : conflicts) {
                rows.add(new Row(text.id(), name(text.id()), c.object().getTextCoordinate().startLine(),
                    c.isDropped() ? "Not kept" : "Kept",
                    describe(c.object(), c.grammarId(), grammarName),
                    describe(c.other(), c.otherGrammarId(), grammarName), c));
            }
        }
        return rows;
    }

    /** Number of overlaps of the project, and how many readings were not kept. */
    public static int[] counts(CorpusView corpus) {
        int all = 0;
        int dropped = 0;
        for (Text text : corpus.texts()) {
            for (MatchConflict c : text.conflicts()) {
                all++;
                if (c.isDropped()) dropped++;
            }
        }
        return new int[] {all, dropped};
    }

    /** "Overlaps", "Overlaps (3)". */
    public static String tabTitle(int count) {
        return count == 0 ? "Overlaps" : "Overlaps (" + count + ")";
    }

    /** "igt — Notebook Grammar, lines 12–15". */
    static String describe(CorpusObject o, String grammarId, Function<String, String> grammarName) {
        String kind = o.getKind();
        int colon = kind.indexOf(':');
        TextCoordinate span = o.getTextCoordinate();
        String lines = span.startLine() == span.endLine() ? "line " + span.startLine()
            : "lines " + span.startLine() + "–" + span.endLine();
        return (colon >= 0 ? kind.substring(colon + 1) : kind) + " — " + grammarName.apply(grammarId) + ", " + lines;
    }

    private static String name(TextId id) {
        var fileName = id.path().getFileName();
        return fileName == null ? id.path().toString() : fileName.toString();
    }
}
