package fr.cnrs.lacito.matchboxlanguage.corpusObject;

import java.util.ArrayList;
import java.util.List;

public class SimpleLexicalDefinition extends CorpusObject {

    private final List<SimpleSense> senses = new ArrayList<>();
    private final String headform;


    public SimpleLexicalDefinition(TextCoordinate coordinate, String headform) {
        super(coordinate);
        this.headform = headform;
    }

    public List<SimpleSense> getSenses() {
        // Placeholder for extracting examples from the context
        return this.senses;
    }

    public void addSense(SimpleSense sense) {
        this.senses.add(sense);
    }

    public String getHeadword() {
        return this.headform;
    }

}
