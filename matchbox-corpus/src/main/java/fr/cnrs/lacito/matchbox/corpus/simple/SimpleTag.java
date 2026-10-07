package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import java.util.List;
import java.util.Objects;

/** An immutable tag: its keyword, and optionally the span of the keyword (making it editable). */
public final class SimpleTag extends AbstractCorpusObject implements Tag, HasFields {

    private final String name;
    private final List<Field> fields;

    public SimpleTag(String kind, TextCoordinate coordinate, String name) {
        this(kind, coordinate, name, List.of());
    }

    /** @param fields span of the keyword ({@link Tag#TAG}), making it editable; empty: read-only */
    public SimpleTag(String kind, TextCoordinate coordinate, String name, List<Field> fields) {
        super(kind, coordinate);
        this.name = Objects.requireNonNull(name, "name").strip();
        this.fields = List.copyOf(fields);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }
}
