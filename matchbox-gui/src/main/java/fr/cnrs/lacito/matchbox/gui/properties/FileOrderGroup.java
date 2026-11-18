package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

/** The order of the files: of the dictionary history (the commands are applied in it) and of the file list. */
final class FileOrderGroup {

    private final VBox node;

    FileOrderGroup(PageContext ctx) {
        ToggleGroup group = new ToggleGroup();
        VBox choices = new VBox(4);
        for (ProjectFileOrder order : ProjectFileOrder.values()) {
            RadioButton b = new RadioButton(order.label());
            b.setId("fileOrder-" + order.xmlName());
            b.setToggleGroup(group);
            b.setUserData(order);
            b.setSelected(ctx.edit().getFileOrder() == order);
            String help = switch (order) {
                case PATH -> "a/b/x.txt before a/y.txt: the files and folders of a folder sorted by name together.";
                case MODIFIED -> "Oldest first. Saving an old file moves it, and its commands, to the end of the history;"
                    + " copying or checking out files may change their times.";
                case HEADER_DATE -> "The 'date' of the file header (date: 2025-07-14), oldest first, then by path;"
                    + " files without a date come last.";
            };
            Label h = new Label(help);
            h.setWrapText(true);
            h.setStyle("-fx-font-size: 0.9em; -fx-padding: 0 0 0 24;"
                + (order == ProjectFileOrder.MODIFIED ? " -fx-text-fill: #9a6700;" : " -fx-text-fill: gray;"));
            choices.getChildren().addAll(b, h);
        }
        group.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n != null) {
                ctx.edit().setFileOrder((ProjectFileOrder) n.getUserData());
                ctx.changed();
            }
        });
        node = DialogParts.group("File order",
            DialogParts.help("The dictionary commands of the notes are applied in this order of the files, then in"
                + " the order of each text; the file list shows it."),
            choices);
    }

    VBox node() {
        return node;
    }
}
