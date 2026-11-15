package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult;
import java.util.Optional;

/**
 * What the project checks read besides the text: the settings, the dictionary the gloss check
 * compares with (the context dictionary, or the patched one when the history is clean), and the
 * last history of the dictionary.
 */
public record CheckInputs(GlossCheckSettings settings, Optional<LexiconIndex> dictionary,
                          Optional<HistoryResult> history) {

    /** Without history (the form used before the dictionary history). */
    public CheckInputs(GlossCheckSettings settings, Optional<LexiconIndex> dictionary) {
        this(settings, dictionary, Optional.empty());
    }

    public static CheckInputs defaults() {
        return new CheckInputs(GlossCheckSettings.defaults(), Optional.empty(), Optional.empty());
    }
}
