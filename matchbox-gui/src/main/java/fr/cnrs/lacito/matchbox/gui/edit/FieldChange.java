package fr.cnrs.lacito.matchbox.gui.edit;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Objects;

/**
 * A change of a field of an object (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}), written
 * in the notation of the object's grammar; one of the changes of a batch edit: a new value
 * ({@link Kind#SET}, the default), a new field ({@link Kind#ADD}, e.g. a metadata entry), a field
 * removed ({@link Kind#REMOVE}) or renamed ({@link Kind#RENAME}, to {@code newField}).
 */
public record FieldChange(CorpusObject object, String field, String value, Kind kind, String newField) {

    public enum Kind { SET, ADD, REMOVE, RENAME }

    public FieldChange {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(newField, "newField");
    }

    /** A new value of a field. */
    public FieldChange(CorpusObject object, String field, String value) {
        this(object, field, value, Kind.SET, "");
    }

    public static FieldChange add(CorpusObject object, String field, String value) {
        return new FieldChange(object, field, value, Kind.ADD, "");
    }

    public static FieldChange remove(CorpusObject object, String field) {
        return new FieldChange(object, field, "", Kind.REMOVE, "");
    }

    public static FieldChange rename(CorpusObject object, String field, String newField) {
        return new FieldChange(object, field, "", Kind.RENAME, newField);
    }
}
