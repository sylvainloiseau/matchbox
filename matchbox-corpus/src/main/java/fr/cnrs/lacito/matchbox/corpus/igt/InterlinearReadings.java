package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The readings of the IGTs of a text ({@link InterlinearResolver}), computed once per {@link Text}
 * (texts are immutable) and kept while the text is in use. Thread-safe.
 */
public final class InterlinearReadings {

    private static final Map<Text, InterlinearReadings> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<InterlinearGlossedText, InterlinearReading> readings;

    private InterlinearReadings(Map<InterlinearGlossedText, InterlinearReading> readings) {
        this.readings = readings;
    }

    public static InterlinearReadings of(Text text) {
        return CACHE.computeIfAbsent(text, t -> new InterlinearReadings(InterlinearResolver.resolve(t)));
    }

    /** The reading of an IGT of this text. */
    public Optional<InterlinearReading> of(InterlinearGlossedText igt) {
        return Optional.ofNullable(readings.get(igt));
    }

    /** The reading of the IGT containing an object (the IGT itself, one of its lines). */
    public Optional<InterlinearReading> containing(CorpusObject object) {
        if (object instanceof InterlinearGlossedText igt) return of(igt);
        return readings.values().stream()
            .filter(r -> r.igt().getChildren().contains(object))
            .findFirst();
    }

    public Collection<InterlinearReading> all() {
        return readings.values();
    }
}
