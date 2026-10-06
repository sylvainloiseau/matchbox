package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Built-in view of sections: title, metadata (own and inherited), outline of the content. */
public class SectionViewFactory implements ObjectViewFactory<Section> {

    @Override
    public Class<Section> getType() {
        return Section.class;
    }

    @Override
    public List<ObjectColumn<Section>> getColumns() {
        return List.of(
            ObjectColumn.editable("Title", Section.TITLE, Section::getTitle),
            new ObjectColumn<>("Metadata", s -> s.getMetadata().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "))),
            new ObjectColumn<>("Content", s -> Integer.toString(s.getContent().size()))
        );
    }

    @Override
    public Node createView(Section section, ViewContext context) {
        // empty for an object of an outdated parse
        Optional<Text> text = context.corpus().textOf(section);

        Label title = new Label(section.getLabel());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em;");
        Label position = new Label(GenericObjectViewFactory.position(section.getTextCoordinate()));
        position.setStyle("-fx-text-fill: #777777;");
        position.setOnMouseClicked(e -> context.selectInEditor(section.getTextCoordinate()));

        VBox box = new VBox(6, new HBox(10, title, position));
        box.setStyle("-fx-padding: 8;");
        text.flatMap(t -> breadcrumb(t, section, context)).ifPresent(box.getChildren()::add);
        metadataGrid(section, text).ifPresent(box.getChildren()::add);
        List<CorpusObject> content = text.map(t -> contentInText(t, section))
            .orElse(List.copyOf(section.getContent()));
        box.getChildren().add(new Label(summary(content)));
        TreeView<CorpusObject> outline = outline(content, context);
        VBox.setVgrow(outline, Priority.ALWAYS);
        box.getChildren().add(outline);
        return box;
    }

    /** Enclosing sections as links, outermost first; empty for a top-level section. */
    private static Optional<Node> breadcrumb(Text text, Section section, ViewContext context) {
        List<Section> path = text.enclosingSections(section);
        if (path.isEmpty()) return Optional.empty();
        HBox crumbs = new HBox(4);
        for (Section s : path) {
            Hyperlink link = new Hyperlink(s.getLabel());
            link.setOnAction(e -> context.select(s));
            crumbs.getChildren().addAll(link, new Label("›"));
        }
        return Optional.of(crumbs);
    }

    /** Own metadata, then the inherited values it does not override (greyed). */
    private static Optional<Node> metadataGrid(Section section, Optional<Text> text) {
        Map<String, String> own = section.getMetadata();
        Map<String, String> inherited = new LinkedHashMap<>(text.map(t -> t.metadataOf(section)).orElse(Map.of()));
        inherited.keySet().removeAll(own.keySet());
        if (own.isEmpty() && inherited.isEmpty()) return Optional.empty();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        int row = 0;
        for (var e : own.entrySet()) addRow(grid, row++, e.getKey(), e.getValue(), false);
        for (var e : inherited.entrySet()) addRow(grid, row++, e.getKey(), e.getValue(), true);
        return Optional.of(grid);
    }

    private static void addRow(GridPane grid, int row, String key, String value, boolean inherited) {
        Label k = new Label(key + ":");
        k.setStyle("-fx-font-weight: bold;" + (inherited ? " -fx-text-fill: #888888;" : ""));
        Label v = new Label(value);
        if (inherited) v.setStyle("-fx-text-fill: #888888;");
        grid.addRow(row, k, v);
    }

    /**
     * Content of the section across grammars, in text order: its own content, plus the top-level
     * objects of other grammars whose innermost enclosing section is this one.
     */
    static List<CorpusObject> contentInText(Text text, Section section) {
        List<CorpusObject> result = new ArrayList<>(section.getContent());
        Set<CorpusObject> own = Collections.newSetFromMap(new IdentityHashMap<>());
        Corpus.selfAndDescendants(section).forEach(own::add);
        for (CorpusObject o : text.objects()) {
            if (own.contains(o)) continue;
            List<Section> enclosing = text.enclosingSections(o);
            if (!enclosing.isEmpty() && enclosing.get(enclosing.size() - 1) == section) {
                result.add(o);
            }
        }
        result.sort(Comparator.comparingInt(o -> o.getTextCoordinate().startIndex()));
        return result;
    }

    /** "2 subsections · 14 objects (10 igt, 4 lexical entry)". */
    static String summary(List<CorpusObject> content) {
        long subsections = content.stream().filter(Section.class::isInstance).count();
        Map<String, Long> byKind = content.stream()
            .filter(o -> !(o instanceof Section))
            .collect(Collectors.groupingBy(o -> Kinds.title(o.getKind()), LinkedHashMap::new, Collectors.counting()));
        long objects = byKind.values().stream().mapToLong(Long::longValue).sum();
        String kinds = byKind.entrySet().stream()
            .map(e -> e.getValue() + " " + e.getKey())
            .collect(Collectors.joining(", "));
        return subsections + (subsections == 1 ? " subsection" : " subsections") + " · "
            + objects + (objects == 1 ? " object" : " objects") + (kinds.isEmpty() ? "" : " (" + kinds + ")");
    }

    private static TreeView<CorpusObject> outline(List<CorpusObject> content, ViewContext context) {
        TreeItem<CorpusObject> root = new TreeItem<>();
        content.forEach(o -> root.getChildren().add(item(o)));
        TreeView<CorpusObject> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(CorpusObject o, boolean empty) {
                super.updateItem(o, empty);
                setText(empty || o == null ? null : o.getLabel() + "   [" + o.getKind() + "]");
            }
        });
        tree.setOnMouseClicked(e -> {
            TreeItem<CorpusObject> selected = tree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) return;
            if (e.getClickCount() == 2) {
                context.select(selected.getValue());
            } else {
                context.selectInEditor(selected.getValue().getTextCoordinate());
            }
        });
        return tree;
    }

    /** Subsections are expanded; other objects show their own children collapsed. */
    private static TreeItem<CorpusObject> item(CorpusObject o) {
        TreeItem<CorpusObject> item = new TreeItem<>(o);
        o.getChildren().forEach(c -> item.getChildren().add(item(c)));
        item.setExpanded(o instanceof Section);
        return item;
    }
}
