package fr.cnrs.lacito.matchbox.corpus.capability;

import java.util.List;
import java.util.Map;

/**
 * An interlinear glossed text (IGT), as written: its own metadata and its lines. How it is read in
 * its context — roles of the lines, inherited metadata, alignment, timing — is given by
 * {@code corpus.igt.InterlinearReadings.of(text).of(igt)}.
 *
 * <p>The {@link InterlinearSentence} getters give the first transcription, gloss and translation of
 * a reading without context (the IGT's own {@code setting}, else the heuristics): an approximation for
 * the features that only know {@link InterlinearSentence}.
 *
 * <p>Fields ({@link fr.cnrs.lacito.matchbox.corpus.HasFields}): {@link #metadataField} per metadata
 * entry, {@link #lineField} per line, {@link #wordField} per word of a segmented line.
 */
public interface InterlinearGlossedText extends InterlinearSentence, HasMetadata {

    /** The metadata written in the IGT's own block, in order; unmodifiable. */
    Map<String, String> metadata();

    /** The lines between the two anchors, in order (blank lines excluded). */
    List<InterlinearLine> lines();

    static String metadataField(String key) {
        return "meta/" + key;
    }

    /** The IGT's own block (no block when it has no metadata); the spans of the fields {@link #metadataField}. */
    @Override
    default java.util.Optional<fr.cnrs.lacito.matchbox.corpus.MetadataBlock> metadataBlock() {
        return metadata().isEmpty() ? java.util.Optional.empty()
            : java.util.Optional.of(fr.cnrs.lacito.matchbox.corpus.MetadataBlock.of(java.util.Optional.empty(), metadata(),
                this, InterlinearGlossedText::metadataField));
    }

    @Override
    default String metadataFieldName(String key) {
        return metadataField(key);
    }

    /** @param index 0-based index in {@link #lines()} */
    static String lineField(int index) {
        return "line/" + index;
    }

    static String wordField(int line, int word) {
        return "line/" + line + "/" + word;
    }
}
