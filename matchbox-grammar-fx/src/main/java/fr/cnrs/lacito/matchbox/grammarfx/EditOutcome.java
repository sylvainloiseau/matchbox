package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import java.util.List;

/** Result of an edit requested by a view through {@link ViewContext}. */
public sealed interface EditOutcome {

    /** A short message for the user; empty for {@link Applied}. */
    String message();

    /** Applied to the document; the view is refreshed when the new parse arrives. */
    record Applied() implements EditOutcome {
        public String message() {
            return "";
        }
    }

    /** The text changed since the object was parsed and the edit could not be found again. */
    record Stale(String message) implements EditOutcome {}

    /** The grammar cannot write this value (e.g. a forbidden character). */
    record Refused(String message) implements EditOutcome {}

    /** The edit would add parse errors; nothing was applied. */
    record Invalid(List<Diagnostic> newDiagnostics) implements EditOutcome {
        public Invalid {
            newDiagnostics = List.copyOf(newDiagnostics);
        }

        public String message() {
            return newDiagnostics.isEmpty() ? "The change would add parse errors"
                : "The change would add parse errors: " + newDiagnostics.get(0).message();
        }
    }

    /** No document, file not writable, no such field… */
    record NotEditable(String message) implements EditOutcome {}

    default boolean isApplied() {
        return this instanceof Applied;
    }
}
