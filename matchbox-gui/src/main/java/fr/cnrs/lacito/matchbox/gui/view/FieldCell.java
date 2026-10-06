package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.function.Consumer;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;

/**
 * Table cell editing a field of a corpus object: Enter (or leaving the cell) writes the value
 * through {@link ViewContext#setField}, Escape cancels. When the change is refused, the cell stays
 * in edit mode with the typed value and the reason is shown.
 */
final class FieldCell extends TableCell<CorpusObject, String> {

    private final String field;
    private final ViewContext context;
    private final Consumer<String> status;
    private TextField editor;
    private boolean committing;

    FieldCell(String field, ViewContext context, Consumer<String> status) {
        this.field = field;
        this.context = context;
        this.status = status;
    }

    private CorpusObject row() {
        return getTableRow() == null ? null : getTableRow().getItem();
    }

    @Override
    public void startEdit() {
        CorpusObject row = row();
        if (row == null || !context.canEdit(row) || !(row instanceof HasFields withFields)
            || withFields.field(field).isEmpty()) {
            return;
        }
        super.startEdit();
        if (!isEditing()) return;
        // the raw value of the field (e.g. without the quotes of a translation)
        String initial = withFields.field(field).map(Field::value).orElse(getItem());
        editor = new TextField(initial);
        editor.setOnAction(e -> commit());
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                e.consume();
            }
        });
        editor.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && isEditing()) commit();
        });
        setText(null);
        setGraphic(editor);
        editor.selectAll();
        editor.requestFocus();
    }

    private void commit() {
        if (committing || editor == null) return;
        CorpusObject row = row();
        if (row == null) {
            cancelEdit();
            return;
        }
        String value = editor.getText();
        committing = true;
        try {
            EditOutcome outcome = context.setField(row, field, value);
            if (outcome.isApplied()) {
                status.accept("");
                cancelEdit();
                // shown until the new parse refreshes the row
                setText(value);
            } else {
                editor.setStyle("-fx-border-color: #d33;");
                editor.setTooltip(new Tooltip(outcome.message()));
                status.accept(outcome.message());
            }
        } finally {
            committing = false;
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        editor = null;
        setGraphic(null);
        setText(getItem());
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        } else if (!isEditing()) {
            setText(item);
            setGraphic(null);
        }
    }
}
