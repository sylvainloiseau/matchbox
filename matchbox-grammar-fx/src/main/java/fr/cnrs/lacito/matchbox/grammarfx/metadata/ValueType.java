package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.util.Objects;

/** How a metadata value is interpreted, displayed and edited (see {@link MetadataSchema}). */
public sealed interface ValueType {

    /** Free text. */
    record Plain() implements ValueType {}

    /** An ISO 8601 date, optionally with a time (see {@link IsoDates}). */
    record Date() implements ValueType {}

    /** A media file (path relative to the project or the text) or an http(s) URL. */
    record Media() implements ValueType {}

    /**
     * Comma-separated values (see {@link ValueLists}), picked from a vocabulary.
     *
     * @param vocabularyId id given to {@link MetadataSchema#vocabulary(String)}
     */
    record ValueList(String vocabularyId) implements ValueType {
        public ValueList {
            Objects.requireNonNull(vocabularyId, "vocabularyId");
        }
    }
}
