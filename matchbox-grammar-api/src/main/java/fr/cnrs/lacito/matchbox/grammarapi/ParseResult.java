package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import java.util.List;

/**
 * What a grammar extracted from one text: top-level corpus objects (nested objects are reached
 * through {@link CorpusObject#getChildren()}) and diagnostics (syntax errors…).
 */
public record ParseResult(List<CorpusObject> objects, List<Diagnostic> diagnostics) {

    public ParseResult {
        objects = List.copyOf(objects);
        diagnostics = List.copyOf(diagnostics);
    }

    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of());
    }
}
