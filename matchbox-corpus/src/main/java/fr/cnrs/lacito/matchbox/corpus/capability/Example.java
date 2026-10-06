package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;

/** An example sentence with its translation. */
public interface Example extends CorpusObject {

    /** Field names (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String TRANSCRIPTION = "transcription";
    String TRANSLATION = "translation";

    String getTranscription();

    String getTranslation();

    @Override
    default String getLabel() {
        return getTranscription();
    }
}
