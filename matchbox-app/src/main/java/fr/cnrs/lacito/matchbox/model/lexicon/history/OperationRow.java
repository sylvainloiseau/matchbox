package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.List;

/**
 * One operation of the history, as the views show it.
 *
 * @param index   its 1-based index in the history (also the position the dictionary is at after it)
 * @param text    the file of its command
 * @param start   the offset of its command in the file
 * @param line    the 1-based line of its command
 * @param verb    the verb ("create", "set"…)
 * @param command the command as the engine renders it
 * @param status  what became of it
 * @param effects what it did, one line each ("created sense", "set gloss@en = pig"…)
 * @param error   the message of its error, or null
 */
public record OperationRow(int index, TextId text, int start, int line, String verb, String command,
                           CommandStatus status, List<String> effects, String error) {

    public OperationRow {
        effects = List.copyOf(effects);
    }
}
