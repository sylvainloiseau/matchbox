package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;
import java.util.Map;

/** A sentence annotated with a dependency tree (e.g. CoNLL-U). */
public interface DependencySentence extends CorpusObject {

    String getSentenceId();

    /** Sentence-level comments ({@code # key = value}), in file order. */
    Map<String, String> getMetadata();

    /** One map per token, field name → value (e.g. ID, FORM, LEMMA, UPOS, HEAD, DEPREL), in column order. */
    List<Map<String, String>> getTokens();

    /** Head index of each token (0 = root, -1 = none, e.g. multiword token ranges). */
    List<Integer> getHeads();

    @Override
    default String getLabel() {
        return getSentenceId();
    }
}
