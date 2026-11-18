package fr.cnrs.lacito.matchbox.gui.properties;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

/** The building blocks shared by the pages of the Project properties dialog (one place for their styles). */
final class DialogParts {

    static final String ERROR_COLOUR = "#b00020";

    private DialogParts() {}

    /** The heading at the top of a page. */
    static Label heading(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 1.15em;");
        l.setWrapText(true);
        return l;
    }

    /** A bold label above a field or a group. */
    static Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    /** An explanation, grey and wrapped. */
    static Label help(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #666666;");
        return l;
    }

    /** A red message, taking no room while hidden. */
    static Label errorLabel() {
        Label l = new Label();
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: " + ERROR_COLOUR + ";");
        l.managedProperty().bind(l.visibleProperty());
        l.setVisible(false);
        return l;
    }

    /** A titled group of controls inside a page. */
    static VBox group(String title, Node... content) {
        VBox box = new VBox(5, bold(title));
        box.getChildren().addAll(content);
        return box;
    }

    /** The content of a page: its heading, then the nodes, with the page's padding. */
    static VBox page(ProjectPropertiesPage page, Node... content) {
        VBox box = new VBox(10, heading(page.heading()));
        box.getChildren().addAll(content);
        box.setPadding(new Insets(12));
        box.setAccessibleText(page.heading());
        return box;
    }

    static Separator separator() {
        Separator s = new Separator();
        s.setPadding(new Insets(4, 0, 4, 0));
        return s;
    }
}
