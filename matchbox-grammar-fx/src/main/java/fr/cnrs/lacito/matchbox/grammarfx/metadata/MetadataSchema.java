package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.util.Locale;

/**
 * Types of metadata values by key, and the vocabularies of list values. Given to the views by
 * {@code ViewContext.metadataSchema()}: the application owns this configuration, views and
 * plugins only use it.
 */
public interface MetadataSchema {

    /** Type of the values of a key (keys are compared case-insensitively). */
    ValueType typeOf(String key);

    /** Known values of a list type; {@link Vocabulary#EMPTY} if unknown, never {@code null}. */
    Vocabulary vocabulary(String vocabularyId);

    /** {@code date} → date, {@code media} → media, {@code speakers} → list "speakers", other keys → plain. */
    MetadataSchema DEFAULT = new MetadataSchema() {
        @Override
        public ValueType typeOf(String key) {
            return switch (key == null ? "" : key.strip().toLowerCase(Locale.ROOT)) {
                case "date" -> new ValueType.Date();
                case "media" -> new ValueType.Media();
                case "speakers" -> new ValueType.ValueList("speakers");
                default -> new ValueType.Plain();
            };
        }

        @Override
        public Vocabulary vocabulary(String vocabularyId) {
            return Vocabulary.EMPTY;
        }
    };
}
