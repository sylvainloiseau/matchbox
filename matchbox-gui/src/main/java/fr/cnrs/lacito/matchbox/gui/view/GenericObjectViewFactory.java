package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** View of any corpus object: kind, position, and the tree of its children. */
public class GenericObjectViewFactory implements ObjectViewFactory<CorpusObject> {

    @Override
    public Class<CorpusObject> getType() {
        return CorpusObject.class;
    }

    @Override
    public Node createView(CorpusObject object, ViewContext context) {
        TextCoordinate span = object.getTextCoordinate();
        Label header = new Label(object.getKind() + " — " + position(span));
        header.setStyle("-fx-font-weight: bold;");

        TreeView<CorpusObject> tree = new TreeView<>(item(object));
        tree.getRoot().setExpanded(true);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(CorpusObject o, boolean empty) {
                super.updateItem(o, empty);
                setText(empty || o == null ? null : o.getLabel() + "   [" + o.getKind() + "]");
            }
        });
        tree.setOnMouseClicked(e -> {
            TreeItem<CorpusObject> selected = tree.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && selected != null) {
                context.selectInEditor(selected.getValue().getTextCoordinate());
            }
        });
        VBox.setVgrow(tree, Priority.ALWAYS);
        VBox box = new VBox(6, header, tree);
        box.setStyle("-fx-padding: 6;");
        return box;
    }

    private static TreeItem<CorpusObject> item(CorpusObject o) {
        TreeItem<CorpusObject> item = new TreeItem<>(o);
        for (CorpusObject child : o.getChildren()) {
            item.getChildren().add(item(child));
        }
        item.setExpanded(true);
        return item;
    }

    static String position(TextCoordinate span) {
        String file = span.text().displayName() + ", ";
        return span.startLine() == span.endLine()
            ? file + "line " + span.startLine()
            : file + "lines " + span.startLine() + "–" + span.endLine();
    }
}
