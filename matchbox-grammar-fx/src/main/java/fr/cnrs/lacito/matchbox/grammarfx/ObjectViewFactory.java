package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;
import javafx.scene.Node;

/**
 * Provides the JavaFX views of one type of corpus object.
 *
 * <p>Plugins register implementations in
 * {@code META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory} and depend on
 * this module with the {@code provided} scope (the plugin jar must not contain JavaFX).
 *
 * <p>The application chooses, for an object of class {@code C}, the factory whose
 * {@link #getType()} is the closest supertype of {@code C} (the class itself first), ties broken
 * by {@link #getPriority()}. Built-in factories exist for the capability interfaces and for
 * {@link CorpusObject} itself, so a plugin only needs a factory for what it wants to show
 * differently.
 *
 * <p>A view that loads FXML must call {@code loader.setClassLoader(getClass().getClassLoader())}:
 * by default {@code FXMLLoader} resolves classes with the application's class loader, which does
 * not see the plugin's classes.
 *
 * @param <T> the rendered type (a class or an interface)
 */
public interface ObjectViewFactory<T extends CorpusObject> {

    /** Class or interface this factory renders. */
    Class<T> getType();

    /**
     * Detailed view of one object, shown in the detail pane. Called on the JavaFX thread; an
     * exception makes the application fall back to the generic view.
     */
    Node createView(T object, ViewContext context);

    /**
     * Shows a re-parsed version of the object in a view created by {@link #createView}, keeping
     * its state (focus, scroll, cell being edited). Called on the JavaFX thread when the object
     * shown in the detail pane is replaced by a new parse.
     *
     * @return false (the default) to have the view rebuilt with {@link #createView}
     */
    default boolean updateView(Node view, T object, ViewContext context) {
        return false;
    }

    /** Columns used when objects of this type are shown in a table. Empty: a single label column. */
    default List<ObjectColumn<T>> getColumns() {
        return List.of();
    }

    /** Higher wins when two factories handle the same type. Built-in factories use 0. */
    default int getPriority() {
        return 0;
    }
}
