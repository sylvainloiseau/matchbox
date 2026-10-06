package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;

/** One sense of a {@link LexicalEntry}: a definition and its examples. */
public interface Sense extends CorpusObject {

    /** Field name (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String DEFINITION = "definition";

    String getDefinition();

    List<? extends Example> getExamples();

    @Override
    default String getLabel() {
        return getDefinition();
    }

    @Override
    default List<? extends CorpusObject> getChildren() {
        return getExamples();
    }
}
