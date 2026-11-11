package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import java.util.List;
import java.util.Optional;

/**
 * How one line of an IGT is read.
 *
 * @param line     the line as written
 * @param index    its index in {@code InterlinearGlossedText.lines()}
 * @param role     what it is
 * @param level    its segmentation; empty for an orthographic line and a translation
 * @param name     its name, from {@code setting}
 * @param language its language: {@code setting}, else {@code olang} / {@code mlang}
 * @param declared true if its role comes from {@code setting}, false if guessed
 * @param reason   why it was read so, for a tooltip ("contains IPA symbols")
 */
public record LineReading(InterlinearLine line, int index, LineRole role, Optional<Level> level,
                          Optional<String> name, Optional<String> language, boolean declared, String reason) {

    /** The words at its level; empty for an orthographic line and a translation. */
    public List<Word> words() {
        return level.map(line::words).orElse(List.of());
    }

    public boolean isSegmented() {
        return level.isPresent();
    }
}
