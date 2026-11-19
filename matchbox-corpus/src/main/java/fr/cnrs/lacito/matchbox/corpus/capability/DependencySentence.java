package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;
import java.util.Map;

/** A sentence annotated with a dependency tree (e.g. CoNLL-U). */
public interface DependencySentence extends HasMetadata {

    String getSentenceId();

    /** Sentence-level comments ({@code # key = value}), in file order. */
    Map<String, String> getMetadata();

    /** Name of the field holding the value of a comment {@code # key = value}. */
    static String metadataField(String key) {
        return "comment:" + key;
    }

    /** The comments as a block (no block without comment); the spans of the fields {@link #metadataField}. */
    @Override
    default java.util.Optional<fr.cnrs.lacito.matchbox.corpus.MetadataBlock> metadataBlock() {
        return getMetadata().isEmpty() ? java.util.Optional.empty()
            : java.util.Optional.of(fr.cnrs.lacito.matchbox.corpus.MetadataBlock.of(java.util.Optional.empty(),
                getMetadata(), this, DependencySentence::metadataField));
    }

    @Override
    default String metadataFieldName(String key) {
        return metadataField(key);
    }

    /** One map per token, field name → value (e.g. ID, FORM, LEMMA, UPOS, HEAD, DEPREL), in column order. */
    List<Map<String, String>> getTokens();

    /** Head index of each token (0 = root, -1 = none, e.g. multiword token ranges). */
    List<Integer> getHeads();

    @Override
    default String getLabel() {
        return getSentenceId();
    }
}
