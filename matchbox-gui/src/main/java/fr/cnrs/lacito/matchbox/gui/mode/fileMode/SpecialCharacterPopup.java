package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

/**
 * "Insert special character…": a popup with a search field (by name or character), the recently
 * used characters, then the groups of the palette. Enter inserts the first character shown, a click
 * inserts one; Esc closes.
 */
public final class SpecialCharacterPopup {

    private static SpecialCharacters palette;

    private SpecialCharacterPopup() {
    }

    private static SpecialCharacters palette() {
        if (palette == null) palette = SpecialCharacters.load();
        return palette;
    }

    /**
     * @param recent  recently used characters, most recent first
     * @param insert  inserts a character (and records it as recent)
     */
    public static Popup show(Window owner, double x, double y, List<String> recent, Consumer<String> insert) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        TextField search = new TextField();
        search.setId("specialCharacterSearch");
        search.setPromptText("Search by name (e.g. \"glottal\", \"tilde\") or paste a character");
        VBox groups = new VBox(6);
        groups.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(groups);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(440, 300);
        VBox root = new VBox(6, search, scroll);
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: white; -fx-border-color: #aaaaaa; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0, 0, 2);");
        Consumer<String> choose = c -> {
            popup.hide();
            insert.accept(c);
        };
        Runnable fill = () -> {
            groups.getChildren().clear();
            String q = search.getText();
            if (q.isBlank()) {
                if (!recent.isEmpty()) {
                    groups.getChildren().add(group("Recently used",
                        recent.stream().map(c -> new SpecialCharacters.Entry(c, SpecialCharacters.nameOf(c), "")).toList(), choose));
                }
                for (SpecialCharacters.Group g : palette().groups()) groups.getChildren().add(group(g.name(), g.entries(), choose));
            } else {
                List<SpecialCharacters.Entry> found = palette().search(q);
                groups.getChildren().add(found.isEmpty() ? new Label("No character matches \"" + q.strip() + "\"")
                    : group(found.size() + (found.size() == 1 ? " character" : " characters"), found, choose));
            }
        };
        search.textProperty().addListener((obs, o, n) -> fill.run());
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            } else if (e.getCode() == KeyCode.ENTER) {
                List<SpecialCharacters.Entry> found = search.getText().isBlank() ? List.of() : palette().search(search.getText());
                if (!found.isEmpty()) choose.accept(found.get(0).character());
                else if (!search.getText().isBlank() && search.getText().strip().codePointCount(0, search.getText().strip().length()) == 1) {
                    choose.accept(search.getText().strip());
                }
            }
        });
        fill.run();
        popup.getContent().add(root);
        popup.show(owner, x, y);
        search.requestFocus();
        return popup;
    }

    private static Node group(String title, List<SpecialCharacters.Entry> entries, Consumer<String> choose) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");
        FlowPane flow = new FlowPane(2, 2);
        for (SpecialCharacters.Entry e : entries) {
            Button b = new Button(e.display());
            b.setStyle("-fx-font-size: 1.2em; -fx-padding: 1 6 1 6; -fx-min-width: 30;");
            b.setTooltip(new Tooltip(e.name() + "  " + codePoints(e.character())));
            b.setAccessibleText(e.name());
            b.setOnAction(ev -> choose.accept(e.character()));
            flow.getChildren().add(b);
        }
        return new VBox(2, label, flow);
    }

    private static String codePoints(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> sb.append(sb.length() == 0 ? "" : " ").append(String.format("U+%04X", cp)));
        return sb.toString();
    }
}
