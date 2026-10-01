package fr.cnrs.lacito.matchbox.assist;

import fr.cnrs.lacito.matchbox.assist.suggestion.TextEdit;
import java.util.Optional;

/**
 * Explanation of a parse error.
 *
 * @param text        the explanation
 * @param fix         a correction that passed the grammar check, if any
 * @param note        why a proposed correction is not offered (empty otherwise)
 */
public record Explanation(String text, Optional<TextEdit> fix, String note) {
}
