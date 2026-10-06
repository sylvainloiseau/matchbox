package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import java.util.List;

public final class SimpleLexicalDefinition extends AbstractCorpusObject implements LexicalEntry, HasFields {

    private final String headword;
    private final List<SimpleSense> senses;
    private final List<Field> fields;

    public SimpleLexicalDefinition(String kind, TextCoordinate coordinate, String headword, List<SimpleSense> senses) {
        this(kind, coordinate, headword, senses, List.of());
    }

    /** @param fields span of the headword ({@link #HEADWORD}), making it editable; empty: read-only */
    public SimpleLexicalDefinition(String kind, TextCoordinate coordinate, String headword, List<SimpleSense> senses,
                                   List<Field> fields) {
        super(kind, coordinate);
        this.headword = headword;
        this.senses = List.copyOf(senses);
        this.fields = List.copyOf(fields);
    }

    @Override
    public String getHeadword() {
        return headword;
    }

    @Override
    public List<SimpleSense> getSenses() {
        return senses;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }
}
