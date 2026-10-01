/**
 * Concepts the application itself works with (lexicon mode, exports, cross-linking, search).
 *
 * <p>Admission rule: an interface is added here only when an application feature needs it. A new
 * grammar-specific object type does not justify a new interface: it implements
 * {@link fr.cnrs.lacito.matchbox.corpus.CorpusObject} directly.
 */
package fr.cnrs.lacito.matchbox.corpus.capability;
