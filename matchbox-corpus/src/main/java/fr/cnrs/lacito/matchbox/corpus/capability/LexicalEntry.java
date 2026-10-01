package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;

/** A dictionary entry: a headword and its senses. */
public interface LexicalEntry extends CorpusObject {

    String getHeadword();

    List<? extends Sense> getSenses();

    @Override
    default String getLabel() {
        return getHeadword();
    }

    @Override
    default List<? extends CorpusObject> getChildren() {
        return getSenses();
    }
}
