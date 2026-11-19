package fr.cnrs.lacito.matchbox.corpus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The metadata block of an object, as written: its key/value pairs in text order, duplicates
 * included (see {@link fr.cnrs.lacito.matchbox.corpus.capability.HasMetadata}).
 *
 * @param span    the block, its delimiters included, when the grammar gives it
 * @param entries the pairs in text order
 */
public record MetadataBlock(Optional<TextCoordinate> span, List<MetadataEntry> entries) {

    public MetadataBlock {
        Objects.requireNonNull(span, "span");
        entries = List.copyOf(entries);
    }

    /** The first entry of a key (exact match). */
    public Optional<MetadataEntry> entry(String key) {
        return entries.stream().filter(e -> e.key().equals(key)).findFirst();
    }

    /** True if the key is written more than once. */
    public boolean hasDuplicate(String key) {
        return entries.stream().filter(e -> e.key().equals(key)).count() > 1;
    }

    /** The values by key, the first one of a duplicated key, in text order. */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        for (MetadataEntry e : entries) values.putIfAbsent(e.key(), e.value());
        return java.util.Collections.unmodifiableMap(values);
    }

    /**
     * A block built from a map of values and the fields of an object (the spans of the values), for
     * the objects that expose their metadata as a map.
     *
     * @param fieldName the name of the field holding the value of a key
     */
    public static MetadataBlock of(Optional<TextCoordinate> span, Map<String, String> values, CorpusObject owner,
                                   java.util.function.Function<String, String> fieldName) {
        List<MetadataEntry> entries = values.entrySet().stream().map(e -> new MetadataEntry(e.getKey(), e.getValue(),
            owner instanceof HasFields f ? f.field(fieldName.apply(e.getKey())).map(Field::span) : Optional.empty()))
            .toList();
        return new MetadataBlock(span, entries);
    }
}
