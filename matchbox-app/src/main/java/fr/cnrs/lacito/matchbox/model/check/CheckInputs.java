package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex;
import java.util.Optional;

/** What the project checks read besides the text: the settings and the context dictionary, if loaded. */
public record CheckInputs(GlossCheckSettings settings, Optional<LexiconIndex> dictionary) {

    public static CheckInputs defaults() {
        return new CheckInputs(GlossCheckSettings.defaults(), Optional.empty());
    }
}
