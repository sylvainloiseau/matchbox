package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;
import java.util.Optional;

/**
 * Objects exposing their values with their spans, which makes them editable field by field
 * (a view replaces the text of a field's span rather than rewriting the whole object).
 */
public interface HasFields {

    /** Fields of the object, in text order; empty if the grammar gave no spans. */
    List<Field> getFields();

    default Optional<Field> field(String name) {
        return getFields().stream().filter(f -> f.name().equals(name)).findFirst();
    }
}
