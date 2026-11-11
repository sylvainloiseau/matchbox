package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.igt.Segmenter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A line of an IGT. Its words are segmented once at both levels ({@link Segmenter}).
 *
 * @see SimpleInterlinearGlossedText
 */
public class SimpleInterlinearLine extends AbstractCorpusObject implements InterlinearLine {

    private final Content content;
    private final int number;
    private final String text;
    private final Optional<Level> segmentation;
    private final Map<Level, List<Word>> words = new EnumMap<>(Level.class);

    /**
     * @param coordinate the span of the line (line ending excluded)
     * @param text       the text of the line; for a translation, the text between the quotes
     * @param textStart  offset of {@code text} in the content (after the opening quote of a translation)
     */
    public SimpleInterlinearLine(String kind, TextCoordinate coordinate, Content content, int number, String text,
                                 int textStart) {
        super(kind, coordinate);
        this.content = Objects.requireNonNull(content, "content");
        this.number = number;
        this.text = Objects.requireNonNull(text, "text");
        if (content == Content.TRANSLATION) {
            this.segmentation = Optional.empty();
        } else {
            this.segmentation = Optional.of(Segmenter.levelOf(text));
            for (Level level : Level.values()) {
                words.put(level, Segmenter.words(coordinate.text(), text, textStart, coordinate.startLine(), level));
            }
        }
    }

    @Override
    public Content content() {
        return content;
    }

    @Override
    public int number() {
        return number;
    }

    @Override
    public String text() {
        return text;
    }

    @Override
    public Optional<Level> segmentation() {
        return segmentation;
    }

    @Override
    public List<Word> words() {
        return segmentation.map(this::words).orElse(List.of());
    }

    @Override
    public List<Word> words(Level level) {
        return words.getOrDefault(level, List.of());
    }

    @Override
    public String getLabel() {
        return text;
    }
}
