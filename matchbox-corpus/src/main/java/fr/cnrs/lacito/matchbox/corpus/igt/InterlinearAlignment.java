package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The alignment of the lines of an IGT once read:
 * <ul>
 *   <li>every segmented line (word and morpheme levels) has the same number of words — the count of
 *       most lines (ties: the first morpheme-level transcription), so the odd line out is reported;</li>
 *   <li>the morpheme-level lines agree on the morphemes of each word, and on their boundaries;</li>
 *   <li>timelines increase; a level has two timelines at most, the second (ends) after the first;</li>
 *   <li>the lines come in order: orthographic, word level (transcription, gloss, timeline), morpheme
 *       level (same), translations.</li>
 * </ul>
 * Orthographic lines and translations are not aligned. Pure.
 */
public final class InterlinearAlignment {

    private InterlinearAlignment() {}

    public static List<InterlinearIssue> check(List<LineReading> lines) {
        List<InterlinearIssue> issues = new ArrayList<>();
        order(lines, issues);
        List<LineReading> segmented = lines.stream().filter(LineReading::isSegmented).toList();
        if (segmented.isEmpty()) return issues;

        int words = reference(segmented, l -> l.words().size());
        for (LineReading l : segmented) {
            int n = l.words().size();
            if (n != words) {
                issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.WORD_COUNT,
                    "Line " + l.line().number() + " (" + roleName(l) + ") has " + n + " word" + (n == 1 ? "" : "s")
                        + ", the other segmented lines have " + words,
                    l.line().getTextCoordinate(), l.index(), -1));
            }
        }

        List<LineReading> morphemeLines = segmented.stream()
            .filter(l -> l.level().orElseThrow() == Level.MORPHEME && l.words().size() == words).toList();
        if (morphemeLines.size() > 1) {
            for (int w = 0; w < words; w++) {
                int word = w;
                int count = reference(morphemeLines, l -> l.words().get(word).morphemes().size());
                Optional<LineReading> boundaryRef = morphemeLines.stream()
                    .filter(l -> l.words().get(word).morphemes().size() == count).findFirst();
                for (LineReading l : morphemeLines) {
                    Word wd = l.words().get(w);
                    int n = wd.morphemes().size();
                    if (n != count) {
                        issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.MORPHEME_COUNT,
                            "Word " + (w + 1) + " has " + n + " morpheme" + (n == 1 ? "" : "s") + ", " + count
                                + " expected (" + boundaryRef.map(r -> r.words().get(word).text()).orElse("") + ")",
                            wd.span(), l.index(), w));
                    } else if (boundaryRef.isPresent() && boundaryRef.get() != l
                        && l.role() != LineRole.TIMELINE && boundaryRef.get().role() != LineRole.TIMELINE
                        && !boundaries(wd).equals(boundaries(boundaryRef.get().words().get(w)))) {
                        issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.BOUNDARY_MISMATCH,
                            "Word " + (w + 1) + ": the boundaries of " + wd.text() + " differ from "
                                + boundaryRef.get().words().get(w).text(),
                            wd.span(), l.index(), w));
                    }
                }
            }
        }
        timelines(lines, issues);
        return issues;
    }

    private static List<Morpheme.Boundary> boundaries(Word w) {
        return w.morphemes().stream().map(Morpheme::boundary).toList();
    }

    /** The value most lines have; ties: the value of the first morpheme-level transcription, else the first line. */
    private static int reference(List<LineReading> lines, java.util.function.ToIntFunction<LineReading> value) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (LineReading l : lines) counts.merge(value.applyAsInt(l), 1, Integer::sum);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> best = counts.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
        if (best.size() == 1) return best.get(0);
        Optional<LineReading> preferred = lines.stream()
            .filter(l -> l.role() == LineRole.TRANSCRIPTION && l.level().orElse(Level.WORD) == Level.MORPHEME
                && best.contains(value.applyAsInt(l)))
            .findFirst();
        return preferred.map(value::applyAsInt).orElseGet(() -> lines.stream().mapToInt(value)
            .filter(best::contains).findFirst().orElse(0));
    }

    /** Rank of a line in the order of a block. */
    static int rank(LineReading l) {
        boolean morpheme = l.level().orElse(Level.WORD) == Level.MORPHEME;
        return switch (l.role()) {
            case ORTHOGRAPHIC -> 0;
            case TRANSCRIPTION -> morpheme ? 4 : 1;
            case GLOSS -> morpheme ? 5 : 2;
            case TIMELINE -> morpheme ? 6 : 3;
            case TRANSLATION -> 7;
        };
    }

    private static void order(List<LineReading> lines, List<InterlinearIssue> issues) {
        LineReading highest = null;
        for (LineReading l : lines) {
            if (highest != null && rank(l) < rank(highest)) {
                issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.ROLE_ORDER,
                    capitalized(roleName(l)) + " (line " + l.line().number() + ") should come before the "
                        + roleName(highest) + " (line " + highest.line().number() + "): orthographic lines, then the word level, "
                        + "then the morpheme level (transcription, gloss, timeline), then the translations",
                    l.line().getTextCoordinate(), l.index(), -1));
            } else if (highest == null || rank(l) > rank(highest)) {
                highest = l;
            }
        }
    }

    private static void timelines(List<LineReading> lines, List<InterlinearIssue> issues) {
        for (Level level : Level.values()) {
            List<LineReading> tl = lines.stream()
                .filter(l -> l.role() == LineRole.TIMELINE && l.level().orElse(Level.WORD) == level).toList();
            for (int k = 2; k < tl.size(); k++) {
                issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.TIMELINE_EXTRA,
                    "A level has two timelines at most (starts, ends): line " + tl.get(k).line().number() + " is ignored",
                    tl.get(k).line().getTextCoordinate(), tl.get(k).index(), -1));
            }
            for (int k = 0; k < Math.min(2, tl.size()); k++) {
                List<Long> values = InterlinearTiming.flatten(tl.get(k));
                for (int i = 1; i < values.size(); i++) {
                    if (values.get(i) >= 0 && values.get(i - 1) >= 0 && values.get(i) < values.get(i - 1)) {
                        issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.TIMELINE_ORDER,
                            "The times of line " + tl.get(k).line().number() + " decrease: " + values.get(i - 1)
                                + " then " + values.get(i), tl.get(k).line().getTextCoordinate(), tl.get(k).index(), -1));
                        break;
                    }
                }
            }
            if (tl.size() >= 2) {
                List<Long> starts = InterlinearTiming.flatten(tl.get(0));
                List<Long> ends = InterlinearTiming.flatten(tl.get(1));
                for (int i = 0; i < Math.min(starts.size(), ends.size()); i++) {
                    if (ends.get(i) >= 0 && starts.get(i) >= 0 && ends.get(i) <= starts.get(i)) {
                        issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.TIMELINE_ORDER,
                            "End " + ends.get(i) + " is not after start " + starts.get(i) + " (position " + (i + 1) + ")",
                            tl.get(1).line().getTextCoordinate(), tl.get(1).index(), -1));
                        break;
                    }
                }
            }
        }
    }

    static String roleName(LineReading l) {
        String level = l.level().map(v -> v == Level.MORPHEME ? "morpheme-level " : "word-level ").orElse("");
        return switch (l.role()) {
            case ORTHOGRAPHIC -> "orthographic line";
            case TRANSCRIPTION -> level + "transcription";
            case GLOSS -> level + "gloss";
            case TIMELINE -> level + "timeline";
            case TRANSLATION -> "translation";
        };
    }

    private static String capitalized(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
