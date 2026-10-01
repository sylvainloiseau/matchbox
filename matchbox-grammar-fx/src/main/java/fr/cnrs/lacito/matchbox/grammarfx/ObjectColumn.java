package fr.cnrs.lacito.matchbox.grammarfx;

import java.util.Objects;
import java.util.function.Function;

/**
 * A table column described independently of JavaFX controls: the application builds the actual
 * {@code TableColumn}.
 *
 * @param title column header
 * @param value text of the cell for an object
 * @param <T>   the object type
 */
public record ObjectColumn<T>(String title, Function<? super T, String> value) {

    public ObjectColumn {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(value, "value");
    }
}
