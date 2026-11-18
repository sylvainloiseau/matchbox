package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import javafx.scene.Node;

/**
 * A tab of the Project properties dialog. A page edits the shared {@link ProjectEdit} working copy
 * and calls {@link PageContext#changed()} after each change; it reads the working copy again when
 * its tab is shown (another page may have changed it), and never talks to another page directly.
 */
interface PropertiesPage {

    ProjectPropertiesPage id();

    /** The content of the tab, built once. */
    Node content();

    /** Called each time the tab is selected. */
    default void shown() {}

    /** Shows the field of a problem of the page (selects what it concerns, focuses the field). */
    default void reveal(ProjectEdit.Problem problem) {}
}
