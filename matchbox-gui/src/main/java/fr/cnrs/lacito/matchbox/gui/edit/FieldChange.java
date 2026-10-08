package fr.cnrs.lacito.matchbox.gui.edit;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Objects;

/**
 * A new value for a field of an object (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}),
 * written in the notation of the object's grammar; one of the changes of a batch edit.
 */
public record FieldChange(CorpusObject object, String field, String value) {

    public FieldChange {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
    }
}
