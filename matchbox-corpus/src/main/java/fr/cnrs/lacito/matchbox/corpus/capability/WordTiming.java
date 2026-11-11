package fr.cnrs.lacito.matchbox.corpus.capability;

import java.util.Optional;

/**
 * When a word (or a morpheme) is said in the media of an IGT, after the {@code offset} of the IGT.
 *
 * @param word     index of the word (0-based)
 * @param morpheme index of the morpheme in the word, -1 at the word level
 * @param range    the range in the media; empty when its end is not known
 */
public record WordTiming(int word, int morpheme, Optional<TimeRange> range) {
}
