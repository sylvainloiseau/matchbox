package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.WordTiming;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How an IGT is read in its context (see {@link InterlinearResolver}).
 *
 * @param igt       the IGT as written
 * @param metadata  its effective metadata: its own and the inherited keys, with their source
 * @param lines     the reading of each of its lines, in order
 * @param issues    what is wrong in the way it is written (alignment, order, setting, pointers)
 * @param formLine  the transcription compared with a dictionary ({@code form=}, else the default choice)
 * @param glossLine the gloss compared with a dictionary ({@code gloss=}, else the default choice)
 * @param pointed   true if {@code form=} and {@code gloss=} chose the lines (else: the default choice)
 * @param timings   the timing of the words of each level that has a timeline
 */
public record InterlinearReading(InterlinearGlossedText igt, Map<String, MetadataValue> metadata,
                                 List<LineReading> lines, List<InterlinearIssue> issues,
                                 Optional<LineReading> formLine, Optional<LineReading> glossLine, boolean pointed,
                                 Map<Level, List<WordTiming>> timings) {

    public Optional<String> value(String key) {
        return Optional.ofNullable(metadata.get(key)).map(MetadataValue::value);
    }

    public List<LineReading> lines(LineRole role) {
        return lines.stream().filter(l -> l.role() == role).toList();
    }

    /** The segmented lines (transcriptions, glosses, timelines) of a level. */
    public List<LineReading> lines(Level level) {
        return lines.stream().filter(l -> l.level().map(level::equals).orElse(false)).toList();
    }

    public Optional<List<WordTiming>> timing(Level level) {
        return Optional.ofNullable(timings.get(level));
    }

    /** True if no issue is about the number of words or morphemes. */
    public boolean isAligned() {
        return issues.stream().noneMatch(i -> i.kind() == InterlinearIssue.Kind.WORD_COUNT
            || i.kind() == InterlinearIssue.Kind.MORPHEME_COUNT);
    }

    /** The morpheme-level transcription, else the word-level one, else the orthographic line; "" if none. */
    public String transcription() {
        return first(LineRole.TRANSCRIPTION, Level.MORPHEME)
            .or(() -> first(LineRole.TRANSCRIPTION, Level.WORD))
            .or(() -> lines(LineRole.ORTHOGRAPHIC).stream().findFirst())
            .map(l -> l.line().text()).orElse("");
    }

    /** The morpheme-level gloss, else the word-level one; "" if none. */
    public String gloss() {
        return first(LineRole.GLOSS, Level.MORPHEME).or(() -> first(LineRole.GLOSS, Level.WORD))
            .map(l -> l.line().text()).orElse("");
    }

    /** The first translation; "" if none. */
    public String translation() {
        return lines(LineRole.TRANSLATION).stream().findFirst().map(l -> l.line().text()).orElse("");
    }

    public Optional<LineReading> first(LineRole role, Level level) {
        return lines.stream().filter(l -> l.role() == role && l.level().map(level::equals).orElse(false)).findFirst();
    }
}
