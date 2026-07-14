package fr.cnrs.lacito.matchbox.language.corpusObject;

public class CorpusObject {

    protected final TextCoordinate coordinate;

    public CorpusObject(TextCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    public TextCoordinate getTextCoordinate() {
        return coordinate;
    }
    
}
