package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearTimeline;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import java.util.List;
import java.util.Optional;

/**
 * A timeline of an IGT, also a media segment covering the IGT (see {@link InterlinearTimeline}).
 */
public final class SimpleInterlinearTimeline extends SimpleInterlinearLine implements InterlinearTimeline {

    private final Optional<String> media;
    private final Optional<TimeRange> range;
    private final List<List<Long>> values;

    /**
     * @param media the IGT's own {@code media} metadata, if any
     * @param range the range of the whole IGT in the media, if known
     */
    public SimpleInterlinearTimeline(String kind, TextCoordinate coordinate, int number, String text, int textStart,
                                     Optional<String> media, Optional<TimeRange> range) {
        super(kind, coordinate, Content.TIMELINE, number, text, textStart);
        this.media = media;
        this.range = range;
        this.values = words().stream().map(w -> w.morphemes().stream().map(SimpleInterlinearTimeline::value).toList()).toList();
    }

    private static Long value(Morpheme m) {
        try {
            return Long.parseLong(m.form());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** The values of a line of numbers, as written ({@code 1 120} or {@code 1-40 120}). */
    public static List<List<Long>> values(List<Word> words) {
        return words.stream().map(w -> w.morphemes().stream().map(SimpleInterlinearTimeline::value).toList()).toList();
    }

    @Override
    public List<List<Long>> values() {
        return values;
    }

    @Override
    public Optional<String> getMediaReference() {
        return media;
    }

    @Override
    public Optional<TimeRange> getRange() {
        return range;
    }

    @Override
    public boolean isWholeMedia() {
        return false;
    }

    @Override
    public String getLabel() {
        return text();
    }
}
