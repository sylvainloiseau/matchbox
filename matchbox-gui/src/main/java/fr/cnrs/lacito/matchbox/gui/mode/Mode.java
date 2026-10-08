package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import java.util.Optional;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Menu;

/**
 * One way to navigate, browse and edit the corpus in the main area of the window (File Mode,
 * later Corpus Mode, Tag Mode…). Modes belong to the application, not to plugins, and work on the
 * capabilities of the objects, whatever plugin created them.
 *
 * <p>A mode is created by its {@link ModeDescriptor} the first time it is shown, with the window's
 * {@link fr.cnrs.lacito.matchbox.gui.workbench.Workbench}, and then kept in memory while the
 * window lives: switching back to it is instant and finds its state again. It owns no thread and
 * no resource to close (those are window services). It changes texts only through the
 * {@code ViewContext}; the window's menus do not depend on it, its own functions go in
 * {@link #menu()}. See {@code .claude/plans/MODE_PLAN.md}.
 */
public interface Mode {

    /** The content of the main area; built once, kept while the window lives. */
    Node root();

    /** The mode is shown. It keeps its own selection from its previous activation. */
    default void activate() {
    }

    /** The mode is hidden. It stays in memory with its state; it should stop costly work. */
    default void deactivate() {
    }

    /**
     * What the window's commands (Save current file, Close file, Play…) target while this mode is
     * active: the text, place and object the user works on in this mode; empty if none.
     */
    ObservableValue<Optional<Focus>> focusProperty();

    /**
     * Shows this place or object in this mode and selects it (navigation: ViewContext.select,
     * Back/Forward, show in editor); false if this mode cannot show it.
     */
    default boolean reveal(Focus focus) {
        return false;
    }

    /**
     * The menu of this mode's own functions, shown in the menu bar while the mode is active;
     * built once. Empty for a mode without specific functions.
     */
    default Optional<Menu> menu() {
        return Optional.empty();
    }
}
