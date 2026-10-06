package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;

/** An interlinear glossed text (IGT) unit: transcription, morpheme glosses and free translation. */
public interface InterlinearSentence extends CorpusObject {

    /** Field names (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String TRANSCRIPTION = "transcription";
    String MORPHEMES = "morphemes";
    String FREE_TRANSLATION = "freeTranslation";

    String getTranscription();

    String getMorphemes();

    String getFreeTranslation();

    @Override
    default String getLabel() {
        return getTranscription();
    }
}
