package fr.cnrs.lacito.matchbox.corpus.gloss;

/** The lists of grammatical labels a project checks its glosses against (see {@link GlossLabels}). */
public enum GlossLabelList {
    /** The appendix of the Leipzig Glossing Rules (84 labels). */
    LEIPZIG,
    /** Christian Lehmann's grammatical category labels. */
    LEHMANN,
    /** Both lists. */
    BOTH,
    /** No check of the labels. */
    NONE
}
