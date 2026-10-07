package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Map;

/**
 * Properties declared in the header of a text (e.g. language, speaker, date). A grammar
 * recognising a header returns it among its top-level objects; {@code Text.properties()} merges
 * the headers of a text.
 */
public interface TextHeader extends CorpusObject {

    /** Name of the field holding the value of a property (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    static String propertyField(String key) {
        return "property:" + key;
    }

    /** Properties in declaration order; unmodifiable, never {@code null}. */
    Map<String, String> getProperties();
}
