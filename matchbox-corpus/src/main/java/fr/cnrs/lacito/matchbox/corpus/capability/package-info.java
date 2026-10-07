/**
 * Concepts the application itself works with (lexicon mode, exports, cross-linking, search).
 *
 * <p>Admission rule: an interface is added here only when an application feature needs it. A new
 * grammar-specific object type does not justify a new interface: it implements
 * {@link fr.cnrs.lacito.matchbox.corpus.CorpusObject} directly.
 *
 * <p>{@link fr.cnrs.lacito.matchbox.corpus.capability.TextHeader} gives the properties of a text,
 * {@link fr.cnrs.lacito.matchbox.corpus.capability.Section} its divisions (title, metadata,
 * content), {@link fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment} the media files and
 * segments it refers to, {@link fr.cnrs.lacito.matchbox.corpus.capability.Tag} keywords attached to places
 * of a text.
 */
package fr.cnrs.lacito.matchbox.corpus.capability;
