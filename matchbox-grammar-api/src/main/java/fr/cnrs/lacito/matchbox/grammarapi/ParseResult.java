package fr.cnrs.lacito.matchbox.grammarapi;

import java.util.List;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;

/**
 * What a grammar extracted from one file: top-level corpus objects (nested objects are reached
 * through {@link CorpusObject#getChildren()}) and syntax errors.
 */
public record ParseResult(List<CorpusObject> objects, List<ParseError> errors) {

    public ParseResult {
        objects = List.copyOf(objects);
        errors = List.copyOf(errors);
    }

    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of());
    }
}
