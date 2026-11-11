package fr.cnrs.lacito.matchbox.corpus.capability;

/**
 * A timeline of an {@link InterlinearGlossedText}: media offsets of its words or morphemes. As a
 * {@link MediaSegment} it covers the whole IGT: from the first start to the last known end, after the
 * {@code offset} of the IGT; its media file is the IGT's own {@code media} metadata, if any.
 */
public interface InterlinearTimeline extends InterlinearLine, MediaSegment {

    /** The values of the line, word by word (one per morpheme on a morpheme-level line); -1 for a value that is not a number. */
    java.util.List<java.util.List<Long>> values();
}
