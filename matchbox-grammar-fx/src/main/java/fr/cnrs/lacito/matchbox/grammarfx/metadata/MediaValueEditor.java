package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaPlayerBox;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Media values: a file shown as a link (click: file chooser) with an audio/video player next to
 * it, or an http(s) URL opened in the browser.
 */
final class MediaValueEditor implements MetadataValueEditor {

    static final MediaValueEditor INSTANCE = new MediaValueEditor();

    @Override
    public Node display(ValueCell cell) {
        if (MediaPaths.isUrl(cell.value())) {
            Hyperlink link = new Hyperlink(cell.value());
            link.setOnAction(e -> {
                link.setVisited(false);
                cell.context().showDocument(cell.value().strip());
            });
            HBox box = new HBox(4, link);
            if (cell.editable()) {
                Button edit = new Button("✎");
                edit.setTooltip(new Tooltip("Change the link, or choose a file"));
                edit.setOnAction(e -> editUrl(cell, edit));
                box.getChildren().add(edit);
            }
            return box;
        }
        MediaPaths.Resolution r = MediaPaths.resolve(cell.value(), cell.projectDirectory(), cell.textDirectory());
        String name = r.path().map(p -> p.getFileName() == null ? p.toString() : p.getFileName().toString())
            .orElse(cell.value());
        Hyperlink link = new Hyperlink(name.isBlank() ? "(choose a file)" : name);
        link.setTooltip(new Tooltip(r.path().map(Path::toString).orElse(cell.value())));
        link.setOnAction(e -> {
            link.setVisited(false);
            if (cell.editable()) {
                choose(cell, link.getScene() == null ? null : link.getScene().getWindow(), r.path())
                    .ifPresent(cell.commit());
            }
        });
        HBox box = new HBox(6, link);
        if (!r.exists()) {
            if (!cell.value().isBlank()) {
                Label missing = new Label("not found");
                missing.setStyle("-fx-text-fill: #c33;");
                box.getChildren().add(missing);
            }
        } else if (MediaPaths.kindOf(r.path().get()) == MediaPaths.Kind.UNSUPPORTED) {
            Label unsupported = new Label("format not supported by the player");
            unsupported.setStyle("-fx-text-fill: #888888;");
            box.getChildren().add(unsupported);
        } else {
            box.getChildren().add(new MediaPlayerBox(r.path().get(), Optional.empty(), cell.context().mediaPlayers()));
        }
        return box;
    }

    @Override
    public Optional<Node> chooser(ValueCell cell, String current, Consumer<String> setValue) {
        Button button = new Button("filechooser");
        button.setOnAction(e -> choose(cell, button.getScene() == null ? null : button.getScene().getWindow(),
            MediaPaths.resolve(current, cell.projectDirectory(), cell.textDirectory()).path()).ifPresent(setValue));
        return Optional.of(button);
    }

    private static void editUrl(ValueCell cell, Node owner) {
        TextInputDialog dialog = new TextInputDialog(cell.value());
        if (owner.getScene() != null) dialog.initOwner(owner.getScene().getWindow());
        dialog.setTitle("Media");
        dialog.setHeaderText("Link (http, https) or file path");
        dialog.showAndWait().ifPresent(cell.commit());
    }

    /** The file chooser; the value to write for the chosen file, if any. */
    static Optional<String> choose(ValueCell cell, Window owner, Optional<Path> current) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Media file");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio and video", "*.mp3", "*.wav", "*.aif", "*.aiff", "*.m4a",
                "*.aac", "*.mp4", "*.m4v", "*.flv"),
            new FileChooser.ExtensionFilter("All files", "*"));
        Optional<Path> dir = current.map(Path::getParent).filter(p -> p.toFile().isDirectory())
            .or(cell::projectDirectory).or(cell::textDirectory);
        dir.map(Path::toFile).filter(File::isDirectory).ifPresent(chooser::setInitialDirectory);
        File file = chooser.showOpenDialog(owner);
        return Optional.ofNullable(file)
            .map(f -> MediaPaths.toValue(f.toPath(), cell.projectDirectory(), cell.textDirectory()));
    }
}
