package fr.cnrs.lacito.matchbox.gui.close;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.util.List;

/**
 * What would be lost by closing a project: its definition (files, file types, plugins, display
 * styles…) and the files with unsaved changes.
 *
 * @param definitionChanged the definition differs from the project file (or was never saved)
 * @param dirty             documents with unsaved changes
 */
public record UnsavedProjectState(boolean definitionChanged, List<TextDocument> dirty) {

    public UnsavedProjectState {
        dirty = List.copyOf(dirty);
    }

    public static UnsavedProjectState of(Project project) {
        return new UnsavedProjectState(project.definitionChangedProperty().get(), project.getDirtyDocuments());
    }

    /** True if nothing would be lost: no question to ask. */
    public boolean isEmpty() {
        return !definitionChanged && dirty.isEmpty();
    }

    /** True if only the definition changed: a yes/no question. */
    public boolean definitionOnly() {
        return definitionChanged && dirty.isEmpty();
    }
}
