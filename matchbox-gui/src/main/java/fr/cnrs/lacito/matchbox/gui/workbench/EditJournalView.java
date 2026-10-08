package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.model.EditJournal;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/** The undo journal of the session's current project; its properties follow the project. */
public final class EditJournalView {

    private final ProjectSession session;
    private final ReadOnlyBooleanWrapper undoAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper redoAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper undoLabel = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper redoLabel = new ReadOnlyStringWrapper("");

    public EditJournalView(ProjectSession session) {
        this.session = session;
        session.projectProperty().addListener((obs, o, n) -> bind(n));
        bind(session.project());
    }

    private void bind(Project project) {
        undoAvailable.unbind();
        redoAvailable.unbind();
        undoLabel.unbind();
        redoLabel.unbind();
        if (project == null) {
            undoAvailable.set(false);
            redoAvailable.set(false);
            undoLabel.set("");
            redoLabel.set("");
            return;
        }
        EditJournal j = project.journal();
        undoAvailable.bind(j.undoAvailableProperty());
        redoAvailable.bind(j.redoAvailableProperty());
        undoLabel.bind(j.undoLabelProperty());
        redoLabel.bind(j.redoLabelProperty());
    }

    /** The journal of the current project, if any. */
    public Optional<EditJournal> journal() {
        return Optional.ofNullable(session.project()).map(Project::journal);
    }

    public ReadOnlyBooleanProperty undoAvailableProperty() {
        return undoAvailable.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty redoAvailableProperty() {
        return redoAvailable.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty undoLabelProperty() {
        return undoLabel.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty redoLabelProperty() {
        return redoLabel.getReadOnlyProperty();
    }
}
