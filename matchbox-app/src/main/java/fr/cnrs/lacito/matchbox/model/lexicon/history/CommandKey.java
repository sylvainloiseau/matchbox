package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;

/**
 * A command of the history: its text and where it starts, which identify it in one parse of
 * the text (a block and its first inner command never start at the same offset).
 */
public record CommandKey(TextId text, int start) {

    public static CommandKey of(LexiconMutationCommand command) {
        return new CommandKey(command.getTextCoordinate().text(), command.getTextCoordinate().startIndex());
    }
}
