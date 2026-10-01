package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;

/** An example sentence with its translation. */
public interface Example extends CorpusObject {

    String getTranscription();

    String getTranslation();

    @Override
    default String getLabel() {
        return getTranscription();
    }
}
