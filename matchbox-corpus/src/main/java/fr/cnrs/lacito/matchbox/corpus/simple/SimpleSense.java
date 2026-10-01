package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import java.util.List;

public final class SimpleSense extends AbstractCorpusObject implements Sense {

    private final String definition;
    private final List<SimpleExample> examples;

    public SimpleSense(String kind, TextCoordinate coordinate, String definition, List<SimpleExample> examples) {
        super(kind, coordinate);
        this.definition = definition;
        this.examples = List.copyOf(examples);
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public List<SimpleExample> getExamples() {
        return examples;
    }
}
