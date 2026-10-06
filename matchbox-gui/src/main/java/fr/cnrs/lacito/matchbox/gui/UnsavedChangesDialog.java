package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Asks what to do with documents that have unsaved changes before they are dropped (file closed,
 * project closed, application quit). Documents can change without being displayed (edits made in
 * object views), so the user may not know they are modified.
 */
public final class UnsavedChangesDialog {

    private UnsavedChangesDialog() {}

    /**
     * @param documents the documents about to be dropped (those without changes are ignored)
     * @param action    what is about to happen, e.g. "closing the project"
     * @return true to go on (changes saved or discarded), false to cancel
     */
    public static boolean confirm(Window owner, List<TextDocument> documents, String action) {
        List<TextDocument> dirty = documents.stream().filter(TextDocument::getCouldBeWritten).toList();
        if (dirty.isEmpty()) {
            return true;
        }
        ButtonType save = new ButtonType(dirty.size() == 1 ? "Save" : "Save all", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard changes", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", save, discard, cancel);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Unsaved changes");
        alert.setHeaderText(dirty.size() == 1
            ? "This file has unsaved changes:"
            : dirty.size() + " files have unsaved changes:");
        alert.setContentText(dirty.stream().map(d -> d.getFilePath().toString()).collect(Collectors.joining("\n"))
            + "\n\nSave them before " + action + "?");
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == cancel) {
            return false;
        }
        if (answer.get() == save) {
            for (TextDocument d : dirty) {
                try {
                    d.writeFile();
                } catch (IOException e) {
                    ErrorReporter.report(owner, "Error saving " + d.getFilePath(), e);
                    return false;
                }
            }
        }
        return true;
    }
}
