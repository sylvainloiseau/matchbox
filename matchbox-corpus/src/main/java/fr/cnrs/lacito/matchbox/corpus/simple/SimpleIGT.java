package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;

/** A three-line group: transcription, morphemes, free translation. */
public final class SimpleIGT extends AbstractCorpusObject implements InterlinearSentence {

    private final String transcription;
    private final String morphemes;
    private final String freeTranslation;

    public SimpleIGT(String kind, TextCoordinate coordinate,
        String transcription,
        String morphemes,
        String freeTranslation
    ) {
        super(kind, coordinate);
        this.transcription = transcription;
        this.morphemes = morphemes;
        this.freeTranslation = freeTranslation;
    }

    @Override
    public String getTranscription() {
        return transcription;
    }

    @Override
    public String getMorphemes() {
        return morphemes;
    }

    @Override
    public String getFreeTranslation() {
        return freeTranslation;
    }
}
