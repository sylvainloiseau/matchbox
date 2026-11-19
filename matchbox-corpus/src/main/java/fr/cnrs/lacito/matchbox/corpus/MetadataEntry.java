package fr.cnrs.lacito.matchbox.corpus;

import java.util.Objects;
import java.util.Optional;

/**
 * One key/value pair of a {@link MetadataBlock}, as written.
 *
 * @param key       the key, as written
 * @param value     the value, as the object exposes it (quotes and escapes removed)
 * @param valueSpan the span of the raw value (see {@link Field}); empty when the grammar gave none
 *                  (the value is then read-only)
 */
public record MetadataEntry(String key, String value, Optional<TextCoordinate> valueSpan) {

    public MetadataEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(valueSpan, "valueSpan");
    }
}
