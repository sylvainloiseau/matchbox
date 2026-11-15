package fr.cnrs.lacito.matchbox.gui.lexicon;

import fr.cnrs.lacito.liftfx.LiftComponentEditor;
import fr.cnrs.lacito.matchbox.model.lexicon.history.EntrySnapshot;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * An entry of the dictionary history in lift-fx's editor, read-only: the editor shows a detached
 * copy (lift-api {@code extract}), never the dictionary the history moves through. Without entry,
 * a placeholder says why.
 */
public final class EntrySnapshotPane extends VBox {

    private final Label placeholder = new Label();

    public EntrySnapshotPane() {
        setSpacing(4);
        setPadding(new Insets(4));
        getStyleClass().add("entry-snapshot");
        placeholder.setWrapText(true);
        placeholder.setStyle("-fx-text-fill: -fx-mid-text-color, gray;");
        showMessage("No entry selected.");
    }

    /** Shows the entry of a snapshot, or the message when it has none. */
    public void show(EntrySnapshot snapshot, String whenAbsent) {
        if (snapshot == null || snapshot.entry().isEmpty() || snapshot.dictionary() == null) {
            showMessage(whenAbsent);
            return;
        }
        LiftComponentEditor editor = new LiftComponentEditor(snapshot.dictionary());
        editor.setReadOnly(true);
        editor.setId("entrySnapshotEditor");
        editor.setComponent(snapshot.entry().get());
        getChildren().setAll(editor);
    }

    public void showMessage(String message) {
        placeholder.setText(message);
        getChildren().setAll(placeholder);
    }
}
