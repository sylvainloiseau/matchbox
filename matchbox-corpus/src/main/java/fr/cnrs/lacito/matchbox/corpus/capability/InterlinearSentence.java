package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;

/** An interlinear glossed text (IGT) unit: transcription, morpheme glosses and free translation. */
public interface InterlinearSentence extends CorpusObject {

    String getTranscription();

    String getMorphemes();

    String getFreeTranslation();

    @Override
    default String getLabel() {
        return getTranscription();
    }
}
