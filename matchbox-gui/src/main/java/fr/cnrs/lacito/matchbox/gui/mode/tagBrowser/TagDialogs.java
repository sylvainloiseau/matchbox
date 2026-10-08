package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import fr.cnrs.lacito.matchbox.gui.edit.FieldChange;

/**
 * The dialogs of the Tag Browser: rename a level, choose a new parent (Move to…), and confirm a
 * move. Each shows what will change before anything is changed, and the obstacles (read-only
 * file, refused value) found by a dry run of the batch edit.
 */
public final class TagDialogs {

    private static final String ERROR = "-fx-text-fill: #b00000;";
    private static final int PREVIEW_LINES = 5;

    private TagDialogs() {
    }

    /** "Renames 12 tags in 4 files" and the first rewrites. */
    public static String preview(TagPathEdit.Change change) {
        StringBuilder sb = new StringBuilder();
        boolean move = change.description().startsWith("Move");
        sb.append(move ? "Moves " : "Renames ").append(change.count()).append(change.count() == 1 ? " tag" : " tags")
            .append(" in ").append(change.files().size()).append(change.files().size() == 1 ? " file" : " files")
            .append(": ").append(TagPathEdit.display(change.from())).append(" → ").append(TagPathEdit.display(change.to()));
        int shown = 0;
        for (TagPathEdit.Rewrite r : change.rewrites()) {
            if (shown++ == PREVIEW_LINES) {
                sb.append("\n   and ").append(change.rewrites().size() - PREVIEW_LINES).append(" more");
                break;
            }
            sb.append("\n   ").append(r.from()).append(" → ").append(r.to()).append("  (").append(r.count()).append(')');
        }
        if (change.merged() > 0) {
            sb.append("\n\n").append(String.join("/", change.to())).append(" exists: its ").append(change.merged())
                .append(change.merged() == 1 ? " tag and these " : " tags and these ").append(change.count())
                .append(" will share the keyword.");
        }
        return sb.toString();
    }

    /**
     * Asks for a new name of the last level of {@code path}; the dialog stays open while the name is
     * refused or the batch edit would fail.
     *
     * @param plan  the rename for a name
     * @param check the obstacles of a batch edit (dry run)
     */
    public static Optional<TagPathEdit.Change> rename(Window owner, List<String> path,
                                                      Function<String, TagPathEdit.Plan> plan,
                                                      Function<List<FieldChange>, List<String>> check) {
        Dialog<TagPathEdit.Change> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Rename tag");
        dialog.setHeaderText("Rename " + TagPathEdit.display(path));
        ButtonType renameType = new ButtonType("Rename", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(renameType, ButtonType.CANCEL);
        TextField name = new TextField(path.get(path.size() - 1));
        name.setPrefColumnCount(30);
        Label preview = new Label();
        preview.setWrapText(true);
        Label error = new Label();
        error.setStyle(ERROR);
        error.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, new Label("New name of this level:"), name, preview, error));
        Node renameButton = dialog.getDialogPane().lookupButton(renameType);
        Runnable update = () -> {
            error.setText("");
            switch (plan.apply(name.getText())) {
                case TagPathEdit.Refused r -> {
                    preview.setText(r.reason());
                    renameButton.setDisable(true);
                }
                case TagPathEdit.Change c -> {
                    preview.setText(preview(c));
                    renameButton.setDisable(false);
                }
            }
        };
        name.textProperty().addListener((obs, o, n) -> update.run());
        update.run();
        renameButton.addEventFilter(ActionEvent.ACTION, e -> {
            Optional<TagPathEdit.Change> change = TagPathEdit.change(plan.apply(name.getText()));
            List<String> problems = change.map(c -> check.apply(c.changes())).orElse(List.of("The name is refused"));
            if (!problems.isEmpty()) {
                error.setText("Nothing was changed:\n" + String.join("\n", problems));
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == renameType ? TagPathEdit.change(plan.apply(name.getText())).orElse(null) : null);
        Platform.runLater(() -> {
            name.requestFocus();
            name.selectAll();
        });
        return dialog.showAndWait();
    }

    /**
     * Confirms a move (drag and drop or Move to…). With obstacles, only Cancel is offered.
     *
     * @return true to apply the move
     */
    public static boolean confirmMove(Window owner, TagPathEdit.Change change, List<String> obstacles) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Move tag");
        dialog.setHeaderText(change.merged() > 0 ? "Move and merge tags" : "Move tags");
        ButtonType moveType = new ButtonType("Move", ButtonBar.ButtonData.OK_DONE);
        Label text = new Label(preview(change));
        text.setWrapText(true);
        VBox content = new VBox(8, text);
        if (obstacles.isEmpty()) {
            dialog.getDialogPane().getButtonTypes().addAll(moveType, ButtonType.CANCEL);
        } else {
            Label error = new Label("These tags cannot be moved:\n" + String.join("\n", obstacles));
            error.setStyle(ERROR);
            error.setWrapText(true);
            content.getChildren().add(error);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        }
        dialog.getDialogPane().setContent(content);
        if (obstacles.isEmpty()) {
            ((Button) dialog.getDialogPane().lookupButton(moveType)).setDefaultButton(true);
        }
        return dialog.showAndWait().filter(b -> b == moveType).isPresent();
    }

    /**
     * Chooses the new parent of {@code node} among the nodes of the tree, or the top level; the
     * refused parents are disabled.
     *
     * @param refusal why a parent is refused; empty if it is accepted
     * @return the new parent's path (empty list: the top level)
     */
    public static Optional<List<String>> chooseParent(Window owner, TagNode root, TagNode node,
                                                      Function<List<String>, Optional<String>> refusal) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Move tag");
        dialog.setHeaderText("Move " + TagPathEdit.display(node.path()) + " under…");
        ButtonType chooseType = new ButtonType("Choose", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(chooseType, ButtonType.CANCEL);
        TextField filter = new TextField();
        filter.setPromptText("Filter tags…");
        TreeView<TagNode> tree = new TreeView<>();
        tree.setPrefSize(380, 360);
        Label reason = new Label();
        reason.setStyle(ERROR);
        tree.setCellFactory(t -> new TreeCell<>() {
            @Override
            protected void updateItem(TagNode n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    setText(null);
                    setDisable(false);
                    return;
                }
                setText(n.isRoot() ? "Top level" : n.level());
                setDisable(refusal.apply(n.path()).isPresent());
            }
        });
        Runnable fill = () -> {
            TreeItem<TagNode> item = item(TagTree.filter(root, filter.getText()));
            item.setExpanded(true);
            if (!filter.getText().isBlank()) expandAll(item);
            tree.setRoot(item);
        };
        filter.textProperty().addListener((obs, o, n) -> fill.run());
        fill.run();
        Node chooseButton = dialog.getDialogPane().lookupButton(chooseType);
        chooseButton.setDisable(true);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            Optional<String> refused = n == null ? Optional.of("") : refusal.apply(n.getValue().path());
            chooseButton.setDisable(refused.isPresent());
            reason.setText(refused.orElse(""));
        });
        dialog.getDialogPane().setContent(new VBox(8, filter, tree, reason));
        dialog.setResultConverter(b -> b == chooseType && tree.getSelectionModel().getSelectedItem() != null
            ? tree.getSelectionModel().getSelectedItem().getValue().path() : null);
        return dialog.showAndWait();
    }

    static TreeItem<TagNode> item(TagNode node) {
        TreeItem<TagNode> item = new TreeItem<>(node);
        List<TreeItem<TagNode>> children = new ArrayList<>();
        for (TagNode child : node.children()) {
            children.add(item(child));
        }
        item.getChildren().setAll(children);
        return item;
    }

    static void expandAll(TreeItem<?> item) {
        item.setExpanded(true);
        item.getChildren().forEach(TagDialogs::expandAll);
    }
}
