package fr.cnrs.lacito.matchboxlanguage.corpusObject;

public class SimpleExample extends CorpusObject {

    private final String transcription;
    private final String translation;

    public SimpleExample(TextCoordinate coordinate, String transcription, String translation) {
        super(coordinate);
        this.transcription = transcription;
        this.translation = translation;
    }

    public String getTranscription() {
        return transcription;
    }

    public String getTranslation() {
        return translation;
    }    
}
