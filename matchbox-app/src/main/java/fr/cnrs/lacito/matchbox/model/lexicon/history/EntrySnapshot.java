package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import java.util.Optional;

/**
 * An entry as it was at a position of the history: a detached copy (lift-api
 * {@code LiftDictionary.extract}), which later recomputes and seeks do not reach, handed to
 * the JavaFX thread.
 *
 * @param position   the position of the timeline (0: before any command)
 * @param dictionary the extract holding the entry (and stubs of the entries it refers to)
 * @param entry      the entry in the extract; empty when it does not exist at that position
 */
public record EntrySnapshot(int position, LiftDictionary dictionary, Optional<LiftEntry> entry) {
}
