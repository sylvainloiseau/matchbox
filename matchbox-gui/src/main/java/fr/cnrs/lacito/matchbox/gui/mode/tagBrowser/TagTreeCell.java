package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import java.util.List;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * A node of the tag tree: its level and, muted, the number of tags of its subtree. Offers the
 * context menu of the node, and drag and drop: a node dropped on another one moves under it.
 */
public final class TagTreeCell extends TreeCell<TagNode> {

    /** What the cell asks the mode to do. */
    public interface Actions {
        void rename(TagNode node);

        void moveTo(TagNode node);

        void expandAll(TagNode node, boolean expanded);

        void copyKeyword(TagNode node);

        /** A node is dragged (null when the drag ends). */
        void dragging(TagNode node);

        /** The node being dragged, if any. */
        Optional<TagNode> dragged();

        /** Why the dragged node cannot go under {@code parent}; empty if it can. */
        Optional<String> refusal(List<String> parent);

        /** The dragged node was dropped on {@code parent}. */
        void drop(List<String> parent);
    }

    private static final String DROP_TARGET = "-fx-background-color: #cfe3ff;";
    private final Actions actions;
    private final PauseTransition expandOnHover = new PauseTransition(Duration.millis(700));

    public TagTreeCell(Actions actions) {
        this.actions = actions;
        setOnDragDetected(e -> {
            if (getItem() == null || getItem().isRoot()) return;
            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(getItem().keyword());
            db.setContent(content);
            Label view = new Label(getItem().level() + " (" + getItem().total() + ")");
            view.setStyle("-fx-background-color: white; -fx-border-color: #888888; -fx-padding: 2 6 2 6;");
            new javafx.scene.Scene(new javafx.scene.Group(view));
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            db.setDragView(view.snapshot(params, null));
            actions.dragging(getItem());
            e.consume();
        });
        setOnDragDone(e -> actions.dragging(null));
        setOnDragOver(e -> {
            Optional<List<String>> target = targetPath();
            if (actions.dragged().isPresent() && target.isPresent() && actions.refusal(target.get()).isEmpty()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        setOnDragEntered(e -> {
            Optional<List<String>> target = targetPath();
            if (actions.dragged().isEmpty() || target.isEmpty()) return;
            Optional<String> refusal = actions.refusal(target.get());
            if (refusal.isEmpty()) {
                setStyle(DROP_TARGET);
            } else {
                setTooltip(new Tooltip(refusal.get()));
            }
            if (getTreeItem() != null && !getTreeItem().isLeaf() && !getTreeItem().isExpanded()) {
                expandOnHover.setOnFinished(f -> getTreeItem().setExpanded(true));
                expandOnHover.playFromStart();
            }
        });
        setOnDragExited(e -> {
            expandOnHover.stop();
            setStyle("");
            update();
        });
        setOnDragDropped(e -> {
            Optional<List<String>> target = targetPath();
            boolean done = actions.dragged().isPresent() && target.isPresent() && actions.refusal(target.get()).isEmpty();
            e.setDropCompleted(done);
            e.consume();
            setStyle("");
            if (done) {
                // the confirmation dialog must not block the end of the drag
                List<String> parent = target.get();
                javafx.application.Platform.runLater(() -> actions.drop(parent));
            }
        });
    }

    /** Where a drop on this cell moves the dragged node: under this node, or the top level for an empty cell. */
    private Optional<List<String>> targetPath() {
        if (isEmpty() || getItem() == null) return Optional.of(List.of());
        return Optional.of(getItem().path());
    }

    @Override
    protected void updateItem(TagNode node, boolean empty) {
        super.updateItem(node, empty);
        update();
    }

    private void update() {
        TagNode node = getItem();
        if (isEmpty() || node == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            return;
        }
        Label level = new Label(node.level());
        Label count = new Label("(" + node.total() + ")");
        count.setStyle("-fx-text-fill: #888888;");
        setText(null);
        setGraphic(new HBox(4, level, count));
        int sub = node.total() - node.own();
        setTooltip(new Tooltip(node.own() + (node.own() == 1 ? " tag " : " tags ") + node.keyword()
            + (sub > 0 ? " · " + sub + " in sub-tags" : "")));
        setContextMenu(contextMenu(node));
        // accessible text: the level and its count
        setAccessibleText(node.level() + ", " + node.total() + " tags");
    }

    private ContextMenu contextMenu(TagNode node) {
        MenuItem rename = new MenuItem("Rename… (F2)");
        rename.setOnAction(e -> actions.rename(node));
        MenuItem move = new MenuItem("Move to… (F6)");
        move.setOnAction(e -> actions.moveTo(node));
        MenuItem expand = new MenuItem("Expand all below");
        expand.setOnAction(e -> actions.expandAll(node, true));
        MenuItem collapse = new MenuItem("Collapse all below");
        collapse.setOnAction(e -> actions.expandAll(node, false));
        MenuItem copy = new MenuItem("Copy keyword");
        copy.setOnAction(e -> actions.copyKeyword(node));
        return new ContextMenu(rename, move, new SeparatorMenuItem(), expand, collapse, new SeparatorMenuItem(), copy);
    }
}
