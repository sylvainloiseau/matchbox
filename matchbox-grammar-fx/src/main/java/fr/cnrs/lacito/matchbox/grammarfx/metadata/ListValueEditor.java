package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

/**
 * Comma-separated values shown as badges: filled for a value of the vocabulary, outlined for
 * another value; × removes a value, "+" adds one from the vocabulary or typed.
 */
final class ListValueEditor implements MetadataValueEditor {

    private static final String KNOWN = "-fx-background-color: #dfe7fd; -fx-background-radius: 10; -fx-padding: 1 6 1 6;";
    private static final String UNKNOWN = "-fx-border-color: #999999; -fx-border-radius: 10; -fx-padding: 0 5 0 5;";

    private final String vocabularyId;

    ListValueEditor(String vocabularyId) {
        this.vocabularyId = vocabularyId;
    }

    @Override
    public Node display(ValueCell cell) {
        Vocabulary vocabulary = cell.schema().vocabulary(vocabularyId);
        List<String> items = ValueLists.split(cell.value());
        boolean flow = ValueLists.isFlow(cell.value());
        FlowPane pane = new FlowPane(4, 4);
        pane.setPrefWrapLength(320);
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            HBox badge = badge(items.get(i), vocabulary);
            if (cell.editable()) {
                Button remove = new Button("×");
                remove.setStyle("-fx-padding: 0 3 0 3; -fx-background-color: transparent;");
                remove.setOnAction(e -> {
                    List<String> left = new ArrayList<>(items);
                    left.remove(index);
                    cell.commit().accept(ValueLists.join(left, flow));
                });
                badge.getChildren().add(remove);
            }
            pane.getChildren().add(badge);
        }
        if (cell.editable()) {
            pane.getChildren().add(addButton(vocabulary, items, value -> {
                List<String> more = new ArrayList<>(items);
                more.add(value);
                cell.commit().accept(ValueLists.join(more, flow));
            }));
        }
        return pane;
    }

    @Override
    public Optional<Node> chooser(ValueCell cell, String current, Consumer<String> setValue) {
        List<String> items = ValueLists.split(current);
        return Optional.of(addButton(cell.schema().vocabulary(vocabularyId), items, value -> {
            List<String> more = new ArrayList<>(ValueLists.split(current));
            more.add(value);
            setValue.accept(ValueLists.join(more, ValueLists.isFlow(current)));
        }));
    }

    private static HBox badge(String value, Vocabulary vocabulary) {
        Optional<Vocabulary.Choice> known = vocabulary.lookup(value);
        Label label = new Label(known.map(Vocabulary.Choice::label).orElse(value));
        known.filter(c -> !c.description().isBlank()).ifPresent(c -> label.setTooltip(new Tooltip(c.description())));
        HBox badge = new HBox(2, label);
        badge.setAlignment(Pos.CENTER_LEFT);
        badge.setStyle(known.isPresent() ? KNOWN : UNKNOWN);
        return badge;
    }

    /** "+": the vocabulary values not used yet, and a field to type another value. */
    private static Button addButton(Vocabulary vocabulary, List<String> used, Consumer<String> add) {
        Button plus = new Button("+");
        plus.setTooltip(new Tooltip("Add a value"));
        plus.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();
            for (Vocabulary.Choice c : vocabulary.choices()) {
                if (used.contains(c.value())) continue;
                MenuItem item = new MenuItem(c.label());
                item.setOnAction(a -> add.accept(c.value()));
                menu.getItems().add(item);
            }
            if (!menu.getItems().isEmpty()) {
                menu.getItems().add(new SeparatorMenuItem());
            }
            TextField typed = new TextField();
            typed.setPromptText("other value, Enter");
            typed.setOnAction(a -> {
                String v = typed.getText().strip();
                menu.hide();
                if (!v.isEmpty()) add.accept(v);
            });
            CustomMenuItem field = new CustomMenuItem(typed, false);
            menu.getItems().add(field);
            menu.show(plus, javafx.geometry.Side.BOTTOM, 0, 0);
            typed.requestFocus();
        });
        return plus;
    }
}
