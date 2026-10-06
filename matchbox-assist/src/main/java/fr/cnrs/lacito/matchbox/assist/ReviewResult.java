package fr.cnrs.lacito.matchbox.assist;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.List;

/**
 * Result of a review.
 *
 * @param accepted  edits that passed the grammar check
 * @param rejected  edits located in the text but that would introduce parse errors
 * @param unlocated number of proposed edits whose original text was not found (or not uniquely)
 */
public record ReviewResult(List<ReviewEdit> accepted, List<ReviewEdit> rejected, int unlocated) {

    /** A proposed edit. {@code kind} is "syntax", "consistency" or "format". */
    public record ReviewEdit(TextEdit edit, String explanation, String kind, int fixedErrors) {}
}
