package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.List;

/** Answer of an {@link ObjectEditor}: the edits to apply, or why the change cannot be written. */
public sealed interface EditProposal {

    /** Edits relative to the content given to the editor. */
    record Edits(List<TextEdit> edits) implements EditProposal {
        public Edits {
            edits = List.copyOf(edits);
        }
    }

    /** The value cannot be written in this notation; {@code reason} is shown to the user. */
    record Refused(String reason) implements EditProposal {}
}
