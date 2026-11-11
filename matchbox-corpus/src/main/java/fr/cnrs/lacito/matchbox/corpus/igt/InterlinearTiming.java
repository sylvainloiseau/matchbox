package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearTimeline;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.capability.WordTiming;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The times of the words (or morphemes) of an IGT, from its timelines: the first timeline of a level
 * gives the starts, the second the ends; with one timeline an item ends where the next starts, the
 * last at the {@code end} metadata, else at the first start of the next IGT, else it has no end. All
 * values are in milliseconds after the {@code offset} metadata. Pure.
 */
public final class InterlinearTiming {

    private InterlinearTiming() {}

    /** The values of a timeline, in order, one per word or morpheme (-1 for a value that is not a number). */
    static List<Long> flatten(LineReading timeline) {
        if (!(timeline.line() instanceof InterlinearTimeline t)) return List.of();
        List<Long> values = new ArrayList<>();
        t.values().forEach(values::addAll);
        return values;
    }

    /**
     * @param offset    the {@code offset} metadata (0 without it)
     * @param end       the {@code end} metadata (absolute, i.e. already after the offset), if any
     * @param nextStart the absolute first start of the next IGT of the text, if any
     */
    static Optional<List<WordTiming>> compute(List<LineReading> lines, Level level, long offset, OptionalLong end,
                                              OptionalLong nextStart) {
        List<LineReading> tl = lines.stream()
            .filter(l -> l.role() == LineRole.TIMELINE && l.level().orElse(Level.WORD) == level).toList();
        if (tl.isEmpty() || !(tl.get(0).line() instanceof InterlinearTimeline first)) return Optional.empty();
        List<Long> ends = tl.size() > 1 ? flatten(tl.get(1)) : List.of();
        List<long[]> items = new ArrayList<>(); // word, morpheme, start
        List<List<Long>> values = first.values();
        for (int w = 0; w < values.size(); w++) {
            for (int m = 0; m < values.get(w).size(); m++) {
                items.add(new long[] {w, level == Level.WORD ? -1 : m, values.get(w).get(m)});
            }
        }
        List<WordTiming> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            long s = items.get(i)[2];
            long e;
            if (i < ends.size()) {
                e = ends.get(i) < 0 ? -1 : offset + ends.get(i);
            } else if (i + 1 < items.size()) {
                e = items.get(i + 1)[2] < 0 ? -1 : offset + items.get(i + 1)[2];
            } else if (end.isPresent()) {
                e = end.getAsLong();
            } else if (nextStart.isPresent()) {
                e = nextStart.getAsLong();
            } else {
                e = -1;
            }
            long start = s < 0 ? -1 : offset + s;
            Optional<TimeRange> range = start >= 0 && e > start ? Optional.of(new TimeRange(start, e)) : Optional.empty();
            result.add(new WordTiming((int) items.get(i)[0], (int) items.get(i)[1], range));
        }
        return Optional.of(List.copyOf(result));
    }
}
