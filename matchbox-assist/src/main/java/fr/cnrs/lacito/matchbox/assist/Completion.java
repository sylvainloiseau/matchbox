package fr.cnrs.lacito.matchbox.assist;

/**
 * A completion that passed the grammar check.
 *
 * @param text        the text to insert at the cursor
 * @param fixedErrors number of parse errors it removes
 */
public record Completion(String text, int fixedErrors) {
}
