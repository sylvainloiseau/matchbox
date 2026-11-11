package fr.cnrs.lacito.matchbox.grammarfx.igt;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.corpus.igt.Segmenter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether a word typed in a cell of the grid fits its column: no space (it would add a word), not
 * empty, and at the morpheme level the number of morphemes of the other lines. Pure.
 */
public final class WordEditCheck {

    private static final TextId NONE = new TextId(Path.of("word"));

    private WordEditCheck() {}

    /** The number of morphemes word {@code word} has on the other morpheme-level lines (the most frequent); empty if none. */
    public static Optional<Integer> expectedMorphemes(InterlinearReading reading, int line, int word) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (LineReading l : reading.lines(Level.MORPHEME)) {
            if (l.index() == line || l.role() == LineRole.TIMELINE) continue;
            List<Word> words = l.words();
            if (word < words.size()) counts.merge(words.get(word).morphemes().size(), 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    /** The problem of the typed word, empty if it fits. */
    public static Optional<String> check(String typed, Level level, Optional<Integer> expected) {
        if (typed.isEmpty()) return Optional.of("A word cannot be empty");
        if (typed.chars().anyMatch(Character::isWhitespace)) {
            return Optional.of("A word has no space: insert a word instead");
        }
        if (level == Level.MORPHEME && expected.isPresent()) {
            int n = Segmenter.words(NONE, typed, 0, 1, Level.MORPHEME).get(0).morphemes().size();
            if (n != expected.get()) {
                return Optional.of(n + " morpheme" + (n == 1 ? "" : "s") + ", the column has " + expected.get());
            }
        }
        return Optional.empty();
    }
}
