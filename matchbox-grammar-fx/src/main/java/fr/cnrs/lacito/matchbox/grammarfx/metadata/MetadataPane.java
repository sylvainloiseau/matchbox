package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Key / value rows of an object's metadata (a section, a text header), editable through a
 * {@link ViewContext}: each value is shown by the editor of its type ({@link MetadataSchema}),
 * own entries can be changed and deleted (×), entries can be added ({@code *}); inherited
 * entries are greyed and read-only.
 *
 * <p>Shared by the views of sections and text headers. Changes go through
 * {@code ViewContext.setField/addField/removeField}: the text is changed and parsed again, and the
 * owning view calls {@link #update} with the new object.
 */
public final class MetadataPane extends VBox {

    private final Function<String, String> fieldName;
    private final ViewContext context;
    private final GridPane grid = new GridPane();
    private final VBox newEntries = new VBox(4);
    private final Label message = new Label();
    private final Button add = new Button("*");
    private CorpusObject owner;
    /** Rows shown, by key and origin, reused by {@link #update} when unchanged (keeps players and edits). */
    private Map<String, Row> rows = new LinkedHashMap<>();

    private record Row(String value, boolean editable, boolean inherited, Node key, Node valueNode, Node delete) {}

    /**
     * @param owner     the object holding the metadata
     * @param own       its own entries, in declaration order
     * @param inherited entries it inherits and does not override (greyed, read-only)
     * @param fieldName key → field name ({@code Section::metadataField}, {@code TextHeader::propertyField})
     */
    public MetadataPane(CorpusObject owner, Map<String, String> own, Map<String, String> inherited,
                        Function<String, String> fieldName, ViewContext context) {
        this.fieldName = Objects.requireNonNull(fieldName);
        this.context = Objects.requireNonNull(context);
        setSpacing(6);
        grid.setHgap(10);
        grid.setVgap(4);
        message.setStyle("-fx-text-fill: #c33;");
        message.setWrapText(true);
        add.setTooltip(new Tooltip("Add an entry"));
        add.setOnAction(e -> addNewEntryRow());
        getChildren().addAll(grid, newEntries, add, message);
        update(owner, own, inherited);
    }

    /** Shows the entries of a re-parsed owner; unchanged rows (and their media players) are kept. */
    public void update(CorpusObject owner, Map<String, String> own, Map<String, String> inherited) {
        this.owner = owner;
        boolean editable = context.canEdit(owner);
        Map<String, Row> next = new LinkedHashMap<>();
        Map<String, Row> previous = new HashMap<>(rows);
        own.forEach((key, value) -> next.put("own:" + key, rowFor("own:" + key, key, value, editable, false, previous)));
        inherited.forEach((key, value) -> {
            if (!own.containsKey(key)) {
                next.put("inherited:" + key, rowFor("inherited:" + key, key, value, false, true, previous));
            }
        });
        rows = next;
        grid.getChildren().clear();
        int r = 0;
        for (Row row : rows.values()) {
            grid.add(row.key(), 0, r);
            grid.add(row.valueNode(), 1, r);
            if (row.delete() != null) grid.add(row.delete(), 2, r);
            r++;
        }
        add.setVisible(editable);
        add.setManaged(editable);
        if (!editable) newEntries.getChildren().clear();
    }

    private Row rowFor(String id, String key, String value, boolean editable, boolean inherited, Map<String, Row> previous) {
        Row old = previous.get(id);
        if (old != null && old.value().equals(value) && old.editable() == editable) {
            return old;
        }
        Label keyLabel = new Label(key + ":");
        keyLabel.setStyle("-fx-font-weight: bold;" + (inherited ? " -fx-text-fill: #888888;" : ""));
        if (inherited) keyLabel.setTooltip(new Tooltip("Inherited"));
        ValueCell cell = new ValueCell(owner, key, value, editable,
            v -> show(context.setField(owner, fieldName.apply(key), v)), context);
        Node valueNode = MetadataValueEditor.of(context.metadataSchema().typeOf(key)).display(cell);
        if (inherited) valueNode.setOpacity(0.6);
        Button delete = null;
        if (editable) {
            delete = new Button("×");
            delete.setTooltip(new Tooltip("Delete this entry"));
            delete.setOnAction(e -> show(context.removeField(owner, fieldName.apply(key))));
        }
        return new Row(value, editable, inherited, keyLabel, valueNode, delete);
    }

    /** A row with a key field and a value field; Enter adds the entry, Escape removes the row. */
    private void addNewEntryRow() {
        TextField key = new TextField();
        key.setPromptText("key");
        key.setPrefColumnCount(10);
        TextField value = new TextField();
        value.setPromptText("value");
        value.setPrefColumnCount(20);
        HBox chooser = new HBox();
        HBox row = new HBox(6, key, value, chooser);
        Runnable updateChooser = () -> {
            chooser.getChildren().clear();
            String k = key.getText().strip();
            if (k.isEmpty()) return;
            ValueCell cell = new ValueCell(owner, k, value.getText(), true, v -> {}, context);
            MetadataValueEditor.of(context.metadataSchema().typeOf(k))
                .chooser(cell, value.getText(), value::setText)
                .ifPresent(chooser.getChildren()::add);
        };
        key.textProperty().addListener((obs, o, n) -> updateChooser.run());
        value.textProperty().addListener((obs, o, n) -> {
            // the list picker appends to the current text
            if (!chooser.getChildren().isEmpty()) updateChooser.run();
        });
        Runnable commit = () -> {
            String k = key.getText().strip();
            if (k.isEmpty()) {
                message.setText("Type a key");
                return;
            }
            EditOutcome outcome = context.addField(owner, fieldName.apply(k), value.getText().strip());
            show(outcome);
            if (outcome.isApplied()) newEntries.getChildren().remove(row);
        };
        key.setOnAction(e -> value.requestFocus());
        value.setOnAction(e -> commit.run());
        row.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                newEntries.getChildren().remove(row);
                message.setText("");
            }
        });
        newEntries.getChildren().add(row);
        key.requestFocus();
    }

    private void show(EditOutcome outcome) {
        message.setText(outcome.isApplied() ? "" : outcome.message());
    }
}
