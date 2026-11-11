package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.util.Locale;

/**
 * A problem of the way an IGT is written, found when it is read in its context.
 *
 * @param kind     what is wrong
 * @param severity always a warning for now: the IGT is still read
 * @param message  human-readable description
 * @param span     where (a line, a word, the metadata)
 * @param line     0-based index of the line in the IGT, -1 if not about a line
 * @param word     0-based index of the word, -1 if about the whole line
 */
public record InterlinearIssue(Kind kind, Diagnostic.Severity severity, String message, TextCoordinate span,
                               int line, int word) {

    public enum Kind {
        /** A segmented line has another number of words than the others. */
        WORD_COUNT,
        /** A word has another number of morphemes than on the other morpheme-level lines. */
        MORPHEME_COUNT,
        /** The boundary symbols of a word differ from the other lines ({@code =} against {@code -}). */
        BOUNDARY_MISMATCH,
        /** A timeline whose values decrease, or an end before its start. */
        TIMELINE_ORDER,
        /** More than two timelines in a level. */
        TIMELINE_EXTRA,
        /** A line not in the order orthographic, word level, morpheme level, translations. */
        ROLE_ORDER,
        /** The {@code setting} cannot be read or does not match the lines. */
        SETTING,
        /** {@code form=} or {@code gloss=} does not point to a suitable line. */
        POINTER;

        /** The code of the diagnostic ({@code igt.word-count}). */
        public String code() {
            return "igt." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public static InterlinearIssue warning(Kind kind, String message, TextCoordinate span, int line, int word) {
        return new InterlinearIssue(kind, Diagnostic.Severity.WARNING, message, span, line, word);
    }
}
