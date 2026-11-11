package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;
import java.util.Optional;

/**
 * A line of an {@link InterlinearGlossedText}, as written. What it is (orthographic line,
 * transcription, gloss…) depends on the metadata of the IGT and of its context: see
 * {@code corpus.igt.InterlinearReadings}.
 */
public interface InterlinearLine extends CorpusObject {

    /** What the characters of a line make it, before any interpretation. */
    enum Content {
        /** Any other line: orthographic line, transcription or gloss. */
        TEXT,
        /** Between single quotes ({@code '…'} or {@code ‘…’}). */
        TRANSLATION,
        /** Only digits, whitespace and boundary symbols. */
        TIMELINE
    }

    Content content();

    /** 1-based number of the line among the non-blank lines between the two anchors. */
    int number();

    /** The text of the line, without its line ending (and without the quotes of a translation). */
    String text();

    /**
     * The segmentation its characters suggest: {@link Level#MORPHEME} when it contains a morpheme
     * boundary ({@code -}, {@code =}, {@code ~}, {@code <…>}), else {@link Level#WORD}; empty for a
     * translation.
     */
    Optional<Level> segmentation();

    /** The words of the line, segmented as {@link #segmentation()} says; empty for a translation. */
    List<Word> words();

    /** The words of the line segmented at the given level (a line may be read at another level than its characters suggest). */
    List<Word> words(Level level);
}
