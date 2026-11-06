package fr.cnrs.lacito.matchbox.corpus;

import java.util.Objects;

/**
 * Characters owned by an {@linkplain MatchRole#EXCLUSIVE exclusive} object kept in a text: the
 * span of an outermost exclusive object (or descendant) of a grammar.
 *
 * @param span      the characters
 * @param grammarId the grammar that read them
 * @param kind      the kind of the object ({@code "<grammar id>:<name>"})
 */
public record Claim(TextCoordinate span, String grammarId, String kind) {

    public Claim {
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(grammarId, "grammarId");
        Objects.requireNonNull(kind, "kind");
    }

    /** True if the claim shares at least one character with {@code [start, stop]} (inclusive). */
    public boolean meets(int start, int stop) {
        return span.startIndex() <= stop && span.stopIndex() >= start;
    }
}
