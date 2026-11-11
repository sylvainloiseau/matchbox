package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.util.List;
import java.util.Objects;

/**
 * A word of a segmented line of an {@link InterlinearGlossedText}.
 *
 * @param text      the word as written
 * @param morphemes its morphemes (one, the whole word, on a word-level line)
 * @param span      its characters
 */
public record Word(String text, List<Morpheme> morphemes, TextCoordinate span) {

    public Word {
        Objects.requireNonNull(text, "text");
        morphemes = List.copyOf(morphemes);
        Objects.requireNonNull(span, "span");
    }
}
