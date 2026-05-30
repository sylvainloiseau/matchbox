package fr.cnrs.lacito.matchboxlanguage.corpusObject;

public class CorpusObject {

    protected final TextCoordinate coordinate;

    public CorpusObject(TextCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    public TextCoordinate getTextCoordinate() {
        return coordinate;
    }
    
}
