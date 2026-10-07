package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaResolver;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MetadataPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * View of text headers: their properties, editable (typed values: dates, media, lists), with a
 * {@code *} button to add one. Registered for {@link TextHeader}, so it shows the header of any
 * grammar while this plugin is installed.
 */
public class TextHeaderViewFactory implements ObjectViewFactory<TextHeader> {

    private static final String PANE = TextHeaderViewFactory.class.getName() + ".pane";
    private static final String POSITION = TextHeaderViewFactory.class.getName() + ".position";
    private static final String MEDIA = TextHeaderViewFactory.class.getName() + ".media";

    @Override
    public Class<TextHeader> getType() {
        return TextHeader.class;
    }

    @Override
    public List<ObjectColumn<TextHeader>> getColumns() {
        return List.of(new ObjectColumn<>("Properties", h -> h.getProperties().entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; "))));
    }

    @Override
    public Node createView(TextHeader header, ViewContext context) {
        Label title = new Label("Header");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em;");
        Label position = new Label(position(header.getTextCoordinate()));
        position.setStyle("-fx-text-fill: #777777;");
        position.setOnMouseClicked(e -> context.selectInEditor(header.getTextCoordinate()));
        MetadataPane pane = new MetadataPane(header, header.getProperties(), Map.of(), TextHeader::propertyField, context);
        VBox media = new VBox(2);
        fillMedia(media, header, context);
        VBox box = new VBox(8, new HBox(10, title, position), pane, media);
        box.setStyle("-fx-padding: 8;");
        box.getProperties().put(PANE, pane);
        box.getProperties().put(POSITION, position);
        box.getProperties().put(MEDIA, media);
        return box;
    }

    /** Keeps the view (rows being edited, media players) when the header is parsed again. */
    @Override
    public boolean updateView(Node view, TextHeader header, ViewContext context) {
        if (!(view.getProperties().get(PANE) instanceof MetadataPane pane)
            || !(view.getProperties().get(POSITION) instanceof Label position)) {
            return false;
        }
        pane.update(header, header.getProperties(), Map.of());
        if (view.getProperties().get(MEDIA) instanceof VBox media) {
            fillMedia(media, header, context);
        }
        position.setText(position(header.getTextCoordinate()));
        position.setOnMouseClicked(e -> context.selectInEditor(header.getTextCoordinate()));
        return true;
    }

    /** "Media used in this text": files of its media segments and "media" metadata. */
    private static void fillMedia(VBox box, TextHeader header, ViewContext context) {
        box.getChildren().clear();
        context.corpus().textOf(header).ifPresent(text -> {
            List<Path> files = MediaResolver.mediaFiles(text, context.corpus(), context.projectDirectory());
            if (files.isEmpty()) return;
            Label title = new Label("Media used in this text:");
            title.setStyle("-fx-font-weight: bold;");
            box.getChildren().add(title);
            for (Path f : files) {
                Label file = new Label(f.getFileName() + (Files.isRegularFile(f) ? "" : "  (not found)"));
                file.setTooltip(new javafx.scene.control.Tooltip(f.toString()));
                if (!Files.isRegularFile(f)) file.setStyle("-fx-text-fill: #c33;");
                box.getChildren().add(file);
            }
        });
    }

    private static String position(TextCoordinate span) {
        String file = span.text().displayName() + ", ";
        return span.startLine() == span.endLine()
            ? file + "line " + span.startLine()
            : file + "lines " + span.startLine() + "–" + span.endLine();
    }
}
