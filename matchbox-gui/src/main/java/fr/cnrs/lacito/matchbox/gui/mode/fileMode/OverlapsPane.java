package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Overlaps tab of File Mode: the readings of the project meeting a reading of another grammar
 * ({@link OverlapRows}); Enter or a double-click shows both in the editor.
 */
public final class OverlapsPane extends VBox {

    static final String EMPTY_TEXT = "No overlapping readings. When two grammars read the same text, "
        + "the first one listed for the file wins and the other gets a warning.";

    private final TableView<OverlapRows.Row> table = new TableView<>();
    private final Function<String, String> grammarName;

    /**
     * @param grammarName name shown for a grammar id
     * @param showBoth    selects the reading kept and outlines the other
     */
    public OverlapsPane(Function<String, String> grammarName, Consumer<OverlapRows.Row> showBoth) {
        this.grammarName = grammarName;
        setSpacing(4);
        setId("overlapsPane");
        Label help = new Label("Readings of the same text by several grammars. The grammar listed first for "
            + "a file wins; change the order in the project properties. Enter or double-click: show both.");
        help.setWrapText(true);
        help.setStyle("-fx-padding: 4 4 0 4; -fx-text-fill: #555555;");

        table.setId("overlapsTable");
        table.getColumns().add(column("File", 140, r -> r.fileName(), r -> r.file().path().toString()));
        TableColumn<OverlapRows.Row, Integer> line = new TableColumn<>("Line");
        line.setPrefWidth(60);
        line.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().line()));
        table.getColumns().add(line);
        table.getColumns().add(column("Outcome", 90, OverlapRows.Row::outcome,
            r -> r.conflict().isDropped() ? "This reading is not in the corpus" : "Both readings are kept"));
        table.getColumns().add(column("Reading", 260, OverlapRows.Row::reading, OverlapRows.Row::reading));
        table.getColumns().add(column("Other reading", 260, OverlapRows.Row::other, OverlapRows.Row::other));
        table.getColumns().add(column("Message", 500, r -> r.conflict().message(), r -> r.conflict().message()));
        Label placeholder = new Label(EMPTY_TEXT);
        placeholder.setWrapText(true);
        table.setPlaceholder(placeholder);
        table.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<OverlapRows.Row>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showBoth.accept(row.getItem());
            });
            return row;
        });
        table.setOnKeyPressed(e -> {
            OverlapRows.Row selected = table.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && selected != null) {
                showBoth.accept(selected);
                e.consume();
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(help, table);
    }

    private static TableColumn<OverlapRows.Row, String> column(String title, double width,
            Function<OverlapRows.Row, String> value, Function<OverlapRows.Row, String> tooltip) {
        TableColumn<OverlapRows.Row, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                OverlapRows.Row r = empty || getTableRow() == null ? null : getTableRow().getItem();
                setTooltip(r == null ? null : new Tooltip(tooltip.apply(r)));
            }
        });
        return c;
    }

    /** Lists the overlaps of the corpus, keeping the selected row's file and line if possible. */
    public void refresh(CorpusView corpus) {
        OverlapRows.Row previous = table.getSelectionModel().getSelectedItem();
        List<OverlapRows.Row> rows = corpus == null ? List.of() : OverlapRows.of(corpus, grammarName);
        table.getItems().setAll(rows);
        if (previous != null) {
            rows.stream().filter(r -> r.file().equals(previous.file()) && r.line() == previous.line()).findFirst()
                .ifPresent(r -> table.getSelectionModel().select(r));
        }
    }

    public TableView<OverlapRows.Row> table() {
        return table;
    }
}
