package fr.cnrs.lacito.matchbox.corpus;

/**
 * What an object does to the characters of its span when several grammars read the same text.
 *
 * <p>The rule (applied by {@link Text.Builder}, grammars in rank order): an {@link #EXCLUSIVE}
 * object of a later grammar may not overlap, include or cross an exclusive object of an earlier
 * grammar; it is then not kept and a {@linkplain Diagnostic.Severity#WARNING warning} says so.
 * {@link #SCOPE} and {@link #ANNOTATION} objects may overlap anything.
 *
 * <p>The role also sets how objects are drawn and clicked (see {@link Text#layered()}): scopes
 * underneath, exclusive objects above them, annotations on top.
 */
public enum MatchRole {
    /** A region giving context to what it contains (a section); drawn underneath. */
    SCOPE,
    /** Content owning its characters (an IGT, a lexical entry, a header); the default. */
    EXCLUSIVE,
    /** A small mark inside content (a tag); drawn on top. */
    ANNOTATION
}
