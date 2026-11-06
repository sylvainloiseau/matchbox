package fr.cnrs.lacito.matchbox.corpus;

import java.util.Objects;

/**
 * Two readings of the same characters by two grammars (or twice by one grammar), found when a
 * text is built (see {@link MatchRole}). {@code object} is the reading of the grammar ranked later
 * for the file; it is not kept when the kind is {@link Kind#DROPPED}.
 *
 * @param kind           what happened
 * @param object         the reading of the later grammar (the one reported)
 * @param grammarId      its grammar
 * @param other          the reading it meets (of an earlier grammar, or of the same one)
 * @param otherGrammarId its grammar
 * @param message        the message of the warning
 */
public record MatchConflict(Kind kind, CorpusObject object, String grammarId,
                            CorpusObject other, String otherGrammarId, String message) {

    /** What the application did with the reading of the later grammar. */
    public enum Kind {
        /** An exclusive top-level object meeting an earlier exclusive one: not kept. */
        DROPPED,
        /** An exclusive object inside a scope or annotation meeting an earlier exclusive one: kept. */
        NESTED_OVERLAP,
        /** A scope crossing an exclusive object of another grammar (neither encloses the other): kept. */
        SCOPE_CROSSING,
        /** Two top-level exclusive objects of the same grammar overlapping: both kept. */
        SAME_GRAMMAR
    }

    public MatchConflict {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(grammarId, "grammarId");
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(otherGrammarId, "otherGrammarId");
        Objects.requireNonNull(message, "message");
    }

    /** True if the reading of the later grammar was not kept. */
    public boolean isDropped() {
        return kind == Kind.DROPPED;
    }

    /** The text both readings belong to. */
    public TextId text() {
        return object.getTextCoordinate().text();
    }
}
