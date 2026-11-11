package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;

/**
 * A corpus object that checks its own content (e.g. a dependency tree whose heads point outside
 * the sentence). Objects are immutable and know neither their text nor any registry: they expose
 * their problems, and {@link Text.Builder} turns the issues of the objects it keeps (and of their
 * children) into {@linkplain Diagnostic.Category#SEMANTIC semantic} diagnostics of their grammar.
 * Objects that are not kept (invalid span, overlapping reading) report nothing.
 *
 * <p>Implement it when the check belongs to the concept, so that every grammar producing the class
 * gets it; a check that depends on the notation is better reported by the grammar itself, with
 * {@link Diagnostic#semantic}.
 */
public interface HasIssues {

    /** The problems of this object's own content (not of its children: they report their own); empty if none. */
    List<Issue> issues();
}
