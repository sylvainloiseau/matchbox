package fr.cnrs.lacito.matchboxlanguage.corpusObject;

import java.util.ArrayList;
import java.util.List;

public class SimpleSense extends CorpusObject {

    private final String definition;
    private final List<SimpleExample> examples = new ArrayList<>();

    public SimpleSense(TextCoordinate coordinate, String definition) {
        super(coordinate);
        this.definition = definition;
    }

    public String getDefinition() {
        return definition;
    }

    public List<SimpleExample> getExamples() {
        return examples;
    }

    public void addExample(SimpleExample example) {
        this.examples.add(example);
    }
}
