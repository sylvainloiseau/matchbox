package fr.cnrs.lacito.matchbox.model.lexicon.history;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the history is computed from.
 *
 * @param dictionary          the context dictionary (empty: start from an empty dictionary)
 * @param dictionaryStamp     its modification time when read (changes force a reload); -1 when none
 * @param scripts             the scripts, in the project's file order (texts without command left out)
 * @param emptyObjectLanguage the object language of an empty dictionary
 * @param emptyMetaLanguage   the meta language of an empty dictionary
 */
public record HistoryInputs(Optional<Path> dictionary, long dictionaryStamp, List<ScriptSource> scripts,
                            String emptyObjectLanguage, String emptyMetaLanguage) {

    public HistoryInputs {
        scripts = List.copyOf(scripts);
    }

    /** Whether the same dictionary is to be read (path, time, and languages of an empty one). */
    boolean sameDictionary(HistoryInputs other) {
        return other != null && dictionary.equals(other.dictionary) && dictionaryStamp == other.dictionaryStamp
            && (dictionary.isPresent() || emptyObjectLanguage.equals(other.emptyObjectLanguage)
                && emptyMetaLanguage.equals(other.emptyMetaLanguage));
    }
}
