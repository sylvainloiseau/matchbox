package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Map;

/**
 * Properties declared in the header of a text (e.g. language, speaker, date). A grammar
 * recognising a header returns it among its top-level objects; {@code Text.properties()} merges
 * the headers of a text.
 */
public interface TextHeader extends CorpusObject {

    /** Properties in declaration order; unmodifiable, never {@code null}. */
    Map<String, String> getProperties();
}
