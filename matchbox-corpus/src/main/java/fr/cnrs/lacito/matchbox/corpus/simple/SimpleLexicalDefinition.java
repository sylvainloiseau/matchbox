package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import java.util.List;

public final class SimpleLexicalDefinition extends AbstractCorpusObject implements LexicalEntry {

    private final String headword;
    private final List<SimpleSense> senses;

    public SimpleLexicalDefinition(String kind, TextCoordinate coordinate, String headword, List<SimpleSense> senses) {
        super(kind, coordinate);
        this.headword = headword;
        this.senses = List.copyOf(senses);
    }

    @Override
    public String getHeadword() {
        return headword;
    }

    @Override
    public List<SimpleSense> getSenses() {
        return senses;
    }
}
