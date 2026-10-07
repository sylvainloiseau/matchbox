package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An immutable text header: properties in declaration order. */
public final class SimpleTextHeader extends AbstractCorpusObject implements TextHeader, HasFields {

    private final Map<String, String> properties;
    private final List<Field> fields;

    public SimpleTextHeader(String kind, TextCoordinate coordinate, Map<String, String> properties) {
        this(kind, coordinate, properties, List.of());
    }

    /**
     * @param fields spans of the property values ({@link TextHeader#propertyField(String)}),
     *               making them editable; empty: read-only
     */
    public SimpleTextHeader(String kind, TextCoordinate coordinate, Map<String, String> properties,
                            List<Field> fields) {
        super(kind, coordinate);
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.fields = List.copyOf(fields);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }

    @Override
    public String getLabel() {
        return "header";
    }
}
