package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;

/** How the values of one {@link ValueType} are displayed and edited in a {@link MetadataPane}. */
public interface MetadataValueEditor {

    /** The value shown in a row; edits are written with {@link ValueCell#commit()}. */
    Node display(ValueCell cell);

    /**
     * A button helping to type the value of a new entry (date picker, file chooser, list picker);
     * {@code setValue} replaces the text of the value field.
     *
     * @param current the text currently in the value field
     */
    default Optional<Node> chooser(ValueCell cell, String current, Consumer<String> setValue) {
        return Optional.empty();
    }

    /** The editor of a value type. */
    static MetadataValueEditor of(ValueType type) {
        return switch (type) {
            case ValueType.Plain p -> PlainValueEditor.INSTANCE;
            case ValueType.Date d -> DateValueEditor.INSTANCE;
            case ValueType.Media m -> MediaValueEditor.INSTANCE;
            case ValueType.ValueList l -> new ListValueEditor(l.vocabularyId());
        };
    }
}
