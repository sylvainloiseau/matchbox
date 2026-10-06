package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable text header: properties in declaration order. */
public final class SimpleTextHeader extends AbstractCorpusObject implements TextHeader {

    private final Map<String, String> properties;

    public SimpleTextHeader(String kind, TextCoordinate coordinate, Map<String, String> properties) {
        super(kind, coordinate);
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String getLabel() {
        return "header";
    }
}
