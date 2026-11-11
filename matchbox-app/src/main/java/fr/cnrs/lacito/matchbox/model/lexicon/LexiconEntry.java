package fr.cnrs.lacito.matchbox.model.lexicon;

import java.util.List;
import java.util.Map;

/**
 * An entry of a {@link Lexicon}.
 *
 * @param id      its identifier in the dictionary
 * @param forms   language → its forms (lexical unit and variants)
 * @param glosses language → the glosses of its senses (subsenses included)
 */
public record LexiconEntry(String id, Map<String, List<String>> forms, Map<String, List<String>> glosses) {

    public LexiconEntry {
        forms = Map.copyOf(forms);
        glosses = Map.copyOf(glosses);
    }
}
