package fr.cnrs.lacito.matchbox.model.lexicon.history;

import java.util.List;
import java.util.Map;

/**
 * An entry of the dictionary as the history computed it, for the table of the mode.
 *
 * @param key       how the entry is found again after a recompute
 * @param forms     its forms by language
 * @param glosses   the glosses of its senses by language, in order
 * @param changed   whether a command changed it
 * @param created   whether a command created it (it is not in the context dictionary)
 * @param operations the indexes of the operations that changed it, in order
 */
public record EntryRow(EntryKey key, Map<String, String> forms, Map<String, List<String>> glosses,
                       boolean changed, boolean created, List<Integer> operations) {

    public EntryRow {
        forms = Map.copyOf(forms);
        glosses = Map.copyOf(glosses);
        operations = List.copyOf(operations);
    }

    /** The form in a language, else the first one. */
    public String form(String language) {
        String f = forms.get(language);
        if (f != null) return f;
        return forms.isEmpty() ? "" : forms.values().iterator().next();
    }

    /** The glosses in a language, joined with "; " (empty when none). */
    public String gloss(String language) {
        List<String> g = glosses.get(language);
        return g == null ? "" : String.join("; ", g);
    }
}
