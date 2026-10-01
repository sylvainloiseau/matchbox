package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Example;

public final class SimpleExample extends AbstractCorpusObject implements Example {

    private final String transcription;
    private final String translation;

    public SimpleExample(String kind, TextCoordinate coordinate, String transcription, String translation) {
        super(kind, coordinate);
        this.transcription = transcription;
        this.translation = translation;
    }

    @Override
    public String getTranscription() {
        return transcription;
    }

    @Override
    public String getTranslation() {
        return translation;
    }
}
