package fr.cnrs.lacito.matchbox.corpus.capability;

/** Segmentation of a line of an {@link InterlinearGlossedText}: into words, or into words and morphemes. */
public enum Level {
    /** Words separated by whitespace. */
    WORD,
    /** Words, and morphemes inside them ({@code -}, {@code =}, {@code ~}, {@code <…>}). */
    MORPHEME
}
