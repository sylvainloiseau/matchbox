package fr.cnrs.lacito.matchbox.grammarfx;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A table column described independently of JavaFX controls: the application builds the actual
 * {@code TableColumn}.
 *
 * @param title column header
 * @param value text of the cell for an object
 * @param field name of the field the column shows (see
 *              {@link fr.cnrs.lacito.matchbox.corpus.HasFields}): when present, the cell can be
 *              edited (Enter commits, Escape cancels) and the change is written through
 *              {@link ViewContext#setField}
 * @param <T>   the object type
 */
public record ObjectColumn<T>(String title, Function<? super T, String> value, Optional<String> field) {

    public ObjectColumn {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(field, "field");
    }

    /** A read-only column. */
    public ObjectColumn(String title, Function<? super T, String> value) {
        this(title, value, Optional.empty());
    }

    /** A column whose cells edit a field of the object. */
    public static <T> ObjectColumn<T> editable(String title, String field, Function<? super T, String> value) {
        return new ObjectColumn<>(title, value, Optional.of(field));
    }
}
