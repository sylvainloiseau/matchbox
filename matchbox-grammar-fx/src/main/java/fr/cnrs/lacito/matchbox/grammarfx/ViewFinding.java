package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.util.List;
import java.util.Objects;

/**
 * A problem the application found about an object (e.g. a gloss unknown to the dictionary), shown
 * by its view: where it is, what it says, the labels of its fixes (applied with
 * {@link ViewContext#applyFix}).
 *
 * @param id       identifies the finding for {@link ViewContext#applyFix}
 * @param span     the characters it is about
 * @param severity how serious
 * @param source   who found it ("Gloss check")
 * @param message  what it says
 * @param fixes    labels of its fixes, in order
 */
public record ViewFinding(String id, TextCoordinate span, Diagnostic.Severity severity, String source, String message,
                          List<String> fixes) {

    public ViewFinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(span, "span");
        fixes = List.copyOf(fixes);
    }
}
