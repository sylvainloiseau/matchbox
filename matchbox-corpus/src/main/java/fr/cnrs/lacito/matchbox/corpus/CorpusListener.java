package fr.cnrs.lacito.matchbox.corpus;

import java.util.Optional;

/** Called after a text was added, replaced or removed. */
@FunctionalInterface
public interface CorpusListener {

    /**
     * @param before the previous snapshot, empty if the text was added
     * @param after  the new snapshot, empty if the text was removed
     */
    void textChanged(TextId id, Optional<Text> before, Optional<Text> after);
}
