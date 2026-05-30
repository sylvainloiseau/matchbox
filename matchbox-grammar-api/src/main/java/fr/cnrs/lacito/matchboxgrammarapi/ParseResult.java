package fr.cnrs.lacito.matchboxgrammarapi;

import java.util.List;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.CorpusObject;

public class ParseResult {

    private final List<CorpusObject> corpusObjects;
    private final List<ParseError> errors;

    public ParseResult(List<CorpusObject> corpusObjects, List<ParseError> errors) {
        this.corpusObjects = corpusObjects;
        this.errors = errors;
    }

    public List<ParseError> getErrors() {
        return errors;
    }

    public List<CorpusObject> getCorpusObjects() {
        return corpusObjects;
    }
}
