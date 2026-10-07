package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

/** Free text: a text field (Enter writes, Escape restores), or a label when not editable. */
final class PlainValueEditor implements MetadataValueEditor {

    static final PlainValueEditor INSTANCE = new PlainValueEditor();

    @Override
    public Node display(ValueCell cell) {
        if (!cell.editable()) {
            Label label = new Label(cell.value());
            label.setWrapText(true);
            return label;
        }
        return textField(cell);
    }

    static TextField textField(ValueCell cell) {
        TextField field = new TextField(cell.value());
        field.setPrefColumnCount(24);
        field.setOnAction(e -> cell.commit().accept(field.getText()));
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) field.setText(cell.value());
        });
        return field;
    }
}
