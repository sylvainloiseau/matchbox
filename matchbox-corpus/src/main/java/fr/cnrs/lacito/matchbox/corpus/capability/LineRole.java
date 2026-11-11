package fr.cnrs.lacito.matchbox.corpus.capability;

/** What a line of an {@link InterlinearGlossedText} is, once read (see {@code corpus.igt.InterlinearReading}). */
public enum LineRole {
    /** The sentence in a spelling whose words need not match the transcription; not segmented. */
    ORTHOGRAPHIC,
    /** The sentence in the object language, segmented. */
    TRANSCRIPTION,
    /** A gloss of each word or morpheme in the meta-language. */
    GLOSS,
    /** A translation in the meta-language, between quotes. */
    TRANSLATION,
    /** Media offsets of each word or morpheme (milliseconds). */
    TIMELINE
}
