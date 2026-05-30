package fr.cnrs.lacito.matchboxlanguage.corpusObject;

/**
 * Represents a three-line group
 */
public class SimpleIGT extends CorpusObject {

    private String transcription;
    private String morpheme;
    private String freeTranslation;

    public SimpleIGT(TextCoordinate coordinate,
        String transcription,
        String morpheme,
        String freeTranslation
    ) {
        super(coordinate);
        this.transcription = transcription;
        this.morpheme = morpheme;
        this.freeTranslation = freeTranslation;
    }

    public String getTranscription() {
        return transcription;
    }
    
    public String getMorpheme() {
        return morpheme;
    }

    public String getFreeTranslation() {
        return freeTranslation;
    }

}
