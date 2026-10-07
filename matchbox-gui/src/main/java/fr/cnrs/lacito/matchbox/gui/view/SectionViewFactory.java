package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MetadataPane;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Built-in view of sections: title, breadcrumb, metadata (own entries editable, inherited ones
 * read-only, shared {@link MetadataPane}), outline of the content.
 */
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

    /** Parts of a section view refreshed by {@link #updateView}. */
    private static final class State {
        final Label title = new Label();
        final Label position = new Label();
        final VBox breadcrumb = new VBox();
        final Label summary = new Label();
        final VBox outline = new VBox();
        MetadataPane metadata;
    }

    private static final String STATE = SectionViewFactory.class.getName();

    @Override
    public Node createView(Section section, ViewContext context) {
        State state = new State();
        state.title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em;");
        state.position.setStyle("-fx-text-fill: #777777;");
        VBox.setVgrow(state.outline, Priority.ALWAYS);
        // own entries editable (dates, media, lists), inherited ones read-only, "*" adds an entry
        state.metadata = new MetadataPane(section, section.getMetadata(), inherited(section, context),
            Section::metadataField, context);
        VBox box = new VBox(6, new HBox(10, state.title, state.position), state.breadcrumb, state.metadata,
            state.summary, state.outline);
        box.setStyle("-fx-padding: 8;");
        box.getProperties().put(STATE, state);
        fill(state, section, context);
        return box;
    }

    /** Shows the re-parsed section in place: the metadata rows being edited and media players are kept. */
    @Override
    public boolean updateView(Node view, Section section, ViewContext context) {
        if (!(view.getProperties().get(STATE) instanceof State state)) {
            return false;
        }
        state.metadata.update(section, section.getMetadata(), inherited(section, context));
        fill(state, section, context);
        return true;
    }

    private static void fill(State state, Section section, ViewContext context) {
        // empty for an object of an outdated parse
        Optional<Text> text = context.corpus().textOf(section);
        state.title.setText(section.getLabel());
        state.position.setText(GenericObjectViewFactory.position(section.getTextCoordinate()));
        state.position.setOnMouseClicked(e -> context.selectInEditor(section.getTextCoordinate()));
        state.breadcrumb.getChildren().setAll(
            text.flatMap(t -> breadcrumb(t, section, context)).map(List::of).orElse(List.of()));
        List<CorpusObject> content = text.map(t -> contentInText(t, section))
            .orElse(List.copyOf(section.getContent()));
        state.summary.setText(summary(content));
        TreeView<CorpusObject> outline = outline(content, context);
        VBox.setVgrow(outline, Priority.ALWAYS);
        state.outline.getChildren().setAll(outline);
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

    /** Metadata the section inherits (text header, enclosing sections) and does not override. */
    private static Map<String, String> inherited(Section section, ViewContext context) {
        Map<String, String> inherited = new LinkedHashMap<>(
            context.corpus().textOf(section).map(t -> t.metadataOf(section)).orElse(Map.of()));
        inherited.keySet().removeAll(section.getMetadata().keySet());
        return inherited;
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
