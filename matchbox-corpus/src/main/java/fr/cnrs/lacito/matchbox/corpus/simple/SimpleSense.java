package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import java.util.List;

public final class SimpleSense extends AbstractCorpusObject implements Sense, HasFields {

    private final String definition;
    private final List<SimpleExample> examples;
    private final List<Field> fields;

    public SimpleSense(String kind, TextCoordinate coordinate, String definition, List<SimpleExample> examples) {
        this(kind, coordinate, definition, examples, List.of());
    }

    /** @param fields span of the definition ({@link #DEFINITION}), making it editable; empty: read-only */
    public SimpleSense(String kind, TextCoordinate coordinate, String definition, List<SimpleExample> examples,
                       List<Field> fields) {
        super(kind, coordinate);
        this.definition = definition;
        this.examples = List.copyOf(examples);
        this.fields = List.copyOf(fields);
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public List<SimpleExample> getExamples() {
        return examples;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }
}
