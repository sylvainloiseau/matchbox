package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import javafx.scene.input.KeyCombination;

/**
 * A mode the window can show: what the toolbar and the View menu need, without creating the mode.
 *
 * @param id          stable identifier ("file", "corpus", "tag")
 * @param title       toolbar button and View menu item ("File")
 * @param menuTitle   title of the mode's menu in the menu bar ("File Mode")
 * @param description tooltip of the button
 * @param shortcut    switches to the mode (Shortcut+1, Shortcut+2…); null for none
 * @param factory     creates the mode, the first time it is shown
 * @param browses     the objects this mode lists (see {@code ViewContext.browse}), known without
 *                    creating the mode; never true for any object if the mode lists none
 */
public record ModeDescriptor(String id, String title, String menuTitle, String description,
                             KeyCombination shortcut, Function<Workbench, Mode> factory,
                             Predicate<CorpusObject> browses) {

    public ModeDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(menuTitle, "menuTitle");
        description = description == null ? "" : description;
        Objects.requireNonNull(factory, "factory");
        browses = browses == null ? o -> false : browses;
    }

    /** A mode that lists no kind of object. */
    public ModeDescriptor(String id, String title, String menuTitle, String description,
                          KeyCombination shortcut, Function<Workbench, Mode> factory) {
        this(id, title, menuTitle, description, shortcut, factory, null);
    }
}
