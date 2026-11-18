package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import javafx.stage.Stage;

/** What the pages of the Project properties dialog share: the working copy and the dialog's navigation. */
interface PageContext {

    Project project();

    ProjectEdit edit();

    /** The dialog's stage (owner of file choosers and sub-dialogs). */
    Stage stage();

    /** To be called after each change of the working copy (updates the tab markers and problems). */
    void changed();

    /** Selects a tab. */
    void select(ProjectPropertiesPage page);

    /** Selects the Plugins tab and the row of a plugin. */
    void showPlugin(String grammarId);
}
