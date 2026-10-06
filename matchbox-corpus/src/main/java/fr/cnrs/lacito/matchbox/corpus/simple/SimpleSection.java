package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An immutable section: title, metadata in declaration order, content in text order. */
public final class SimpleSection extends AbstractCorpusObject implements Section, HasFields {

    private final String title;
    private final Map<String, String> metadata;
    private final List<CorpusObject> content;
    private final List<Field> fields;

    /**
     * @param title    title, {@code null} or empty for an untitled section
     * @param metadata keys and values, neither {@code null}; the iteration order is kept
     * @param content  objects and subsections of this grammar inside the section, in text order
     */
    public SimpleSection(String kind, TextCoordinate coordinate, String title,
                         Map<String, String> metadata, List<? extends CorpusObject> content) {
        this(kind, coordinate, title, metadata, content, List.of());
    }

    /**
     * @param fields spans of the title ({@link Section#TITLE}) and of the metadata values
     *               ({@link Section#metadataField(String)}), making them editable
     */
    public SimpleSection(String kind, TextCoordinate coordinate, String title,
                         Map<String, String> metadata, List<? extends CorpusObject> content,
                         List<Field> fields) {
        super(kind, coordinate);
        this.title = title == null ? "" : title;
        Map<String, String> copy = new LinkedHashMap<>();
        metadata.forEach((k, v) -> copy.put(Objects.requireNonNull(k, "metadata key"),
                                            Objects.requireNonNull(v, "metadata value")));
        // Map.copyOf would lose the order
        this.metadata = Collections.unmodifiableMap(copy);
        this.content = List.copyOf(content);
        this.fields = List.copyOf(fields);
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public List<CorpusObject> getContent() {
        return content;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }
}
