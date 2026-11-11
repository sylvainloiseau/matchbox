package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A problem of the content of a corpus object (see {@link HasIssues}), turned into a
 * {@linkplain Diagnostic.Category#SEMANTIC semantic} {@link Diagnostic} when the text is built.
 *
 * @param message  human-readable description
 * @param severity how serious it is
 * @param span     the offending characters, inside the object's span; empty for the whole object
 * @param code     stable identifier of the kind of problem (see {@link Diagnostic#code()}), empty if none
 * @param fixes    edits correcting it, relative to the content the object was parsed from
 */
public record Issue(String message, Diagnostic.Severity severity, Optional<TextCoordinate> span, String code,
                    List<Diagnostic.Fix> fixes) {

    public Issue {
        Objects.requireNonNull(message, "message");
        severity = severity == null ? Diagnostic.Severity.ERROR : severity;
        span = span == null ? Optional.empty() : span;
        code = code == null ? "" : code;
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }

    /** An error on the whole object. */
    public static Issue error(String message, String code) {
        return new Issue(message, Diagnostic.Severity.ERROR, Optional.empty(), code, List.of());
    }

    /** A warning on the whole object. */
    public static Issue warning(String message, String code) {
        return new Issue(message, Diagnostic.Severity.WARNING, Optional.empty(), code, List.of());
    }

    /** The same issue on a part of the object. */
    public Issue at(TextCoordinate newSpan) {
        return new Issue(message, severity, Optional.ofNullable(newSpan), code, fixes);
    }

    /** The same issue with fixes. */
    public Issue withFixes(List<Diagnostic.Fix> newFixes) {
        return new Issue(message, severity, span, code, newFixes);
    }
}
