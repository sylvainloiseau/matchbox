package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;

/**
 * Anything a grammar extracts from a text and anchors to a span of it.
 *
 * <p>Grammars can implement this interface directly with their own types; the application only
 * needs the span, the kind and the label to highlight, list and navigate them. Concepts the
 * application works with (lexical entries, interlinear sentences…) have dedicated interfaces in
 * {@link fr.cnrs.lacito.matchbox.corpus.capability}.
 *
 * <p>Rules for implementations:
 * <ul>
 *   <li>{@link #getKind()} is {@code "<grammarId>:<name>"}, where {@code <grammarId>} is the
 *       {@code getId()} of the grammar producing the object;</li>
 *   <li>a child's span lies inside its parent's span;</li>
 *   <li>objects are immutable once returned by the grammar, and their collections unmodifiable;</li>
 *   <li>objects keep no reference to ANTLR parse trees, tokens or token streams (copy the text
 *       out), otherwise every parse tree of the project stays in memory.</li>
 * </ul>
 */
public interface CorpusObject {

    /** Span in the source file, used for highlighting and navigation. */
    TextCoordinate getTextCoordinate();

    /** Namespaced, stable type id: {@code "<grammarId>:<name>"}, e.g. {@code "notebook-grammar:igt"}. */
    String getKind();

    /** Short human-readable text for lists, trees and status bars. */
    default String getLabel() {
        return getKind();
    }

    /** Nested anchored objects (senses of an entry, examples of a sense…). */
    default List<? extends CorpusObject> getChildren() {
        return List.of();
    }
}
