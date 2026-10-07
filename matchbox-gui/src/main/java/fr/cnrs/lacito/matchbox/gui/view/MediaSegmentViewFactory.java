package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.grammarapi.TimeCodes;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.tags.TagBadges;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaPlayerBox;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaResolver;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MediaPaths;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Built-in view of media segments (any object implementing {@link MediaSegment}): caption, media
 * file (and where it comes from), player of the range or of the whole file, editable times with
 * nudges and "set from the player position". Edits use the field names of {@link MediaSegment},
 * so they work for any grammar whose ObjectEditor supports them.
 */
public class MediaSegmentViewFactory implements ObjectViewFactory<MediaSegment> {

    private static final String STATE = MediaSegmentViewFactory.class.getName();
    private static final long NUDGE_MS = 100;

    /** The root of a view, refilled by {@link #updateView}; keeps a pending start for "mark a segment". */
    private static final class State {
        Long markedStart;
    }

    @Override
    public Class<MediaSegment> getType() {
        return MediaSegment.class;
    }

    @Override
    public List<ObjectColumn<MediaSegment>> getColumns() {
        return List.of(
            ObjectColumn.editable("Start", MediaSegment.START,
                s -> s.getRange().map(r -> TimeCodes.display(r.startMs())).orElse("whole file")),
            ObjectColumn.editable("End", MediaSegment.END,
                s -> s.getRange().map(r -> TimeCodes.display(r.endMs())).orElse("")),
            new ObjectColumn<>("Duration", s -> s.getRange().map(r -> TimeCodes.display(r.durationMs())).orElse("")),
            ObjectColumn.editable("Caption", MediaSegment.CAPTION, MediaSegment::getCaption),
            new ObjectColumn<>("Media", s -> s.getMediaReference().orElse("(from the metadata)")));
    }

    @Override
    public Node createView(MediaSegment segment, ViewContext context) {
        VBox root = new VBox(8);
        root.setStyle("-fx-padding: 8;");
        root.getProperties().put(STATE, new State());
        fill(root, segment, context);
        return root;
    }

    /** The player is shared: refilling the view after a re-parse does not interrupt playback. */
    @Override
    public boolean updateView(Node view, MediaSegment segment, ViewContext context) {
        if (!(view instanceof VBox root) || !(root.getProperties().get(STATE) instanceof State)) {
            return false;
        }
        fill(root, segment, context);
        return true;
    }

    private static void fill(VBox root, MediaSegment segment, ViewContext context) {
        State state = (State) root.getProperties().get(STATE);
        boolean editable = context.canEdit(segment);
        MediaResolver.Resolution media = MediaResolver.resolve(segment, context.corpus(), context.projectDirectory());
        Label message = new Label();
        message.setStyle("-fx-text-fill: #c33;");
        message.setWrapText(true);

        // caption
        Node title;
        if (editable && has(segment, MediaSegment.CAPTION)) {
            TextField caption = new TextField(segment.getCaption());
            caption.setPromptText("caption");
            caption.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
            caption.setOnAction(e -> show(message, context.setField(segment, MediaSegment.CAPTION, caption.getText())));
            title = caption;
        } else {
            Label caption = new Label(segment.getCaption().isEmpty() ? segment.getLabel() : segment.getCaption());
            caption.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
            title = caption;
        }
        Label position = new Label(GenericObjectViewFactory.position(segment.getTextCoordinate()));
        position.setStyle("-fx-text-fill: #777777;");
        position.setOnMouseClicked(e -> context.selectInEditor(segment.getTextCoordinate()));

        // media file
        HBox mediaLine = new HBox(6, new Label("Media:"));
        mediaLine.setAlignment(Pos.CENTER_LEFT);
        String name = media.file().path().map(p -> p.getFileName() == null ? p.toString() : p.getFileName().toString())
            .or(() -> media.reference()).orElse("(none)");
        Hyperlink link = new Hyperlink(name);
        media.file().path().ifPresent(p -> link.setTooltip(new Tooltip(p.toString())));
        link.setOnAction(e -> {
            link.setVisited(false);
            if (media.isUrl()) {
                context.showDocument(media.reference().orElseThrow());
            } else if (editable && has(segment, MediaSegment.MEDIA)) {
                chooseFile(link, segment, context, media).ifPresent(v ->
                    show(message, context.setField(segment, MediaSegment.MEDIA, v)));
            }
        });
        mediaLine.getChildren().add(link);
        Label source = new Label(switch (media.source()) {
            case EXPLICIT -> "";
            case SECTION -> "(from section \"" + media.sourceTitle().orElse("") + "\")";
            case HEADER -> "(from the header)";
            case NONE -> "";
        });
        source.setStyle("-fx-text-fill: #777777;");
        mediaLine.getChildren().add(source);

        Node player;
        Optional<MediaPlayerBox> box = Optional.empty();
        if (media.source() == MediaResolver.Source.NONE) {
            player = hint("No media file: write one after @media, or a \"media:\" entry in the section or the header.");
        } else if (media.isUrl()) {
            player = hint("Remote media are not played; the link opens the browser.");
        } else if (!media.file().exists()) {
            player = hint("File not found: " + media.file().path().map(Path::toString).orElse(name));
        } else if (MediaPaths.kindOf(media.file().path().get()) == MediaPaths.Kind.UNSUPPORTED) {
            player = hint("Format not supported by the player");
        } else {
            MediaPlayerBox b = new MediaPlayerBox(media.file().path().get(), segment.getRange(), context.mediaPlayers());
            box = Optional.of(b);
            player = b;
        }

        root.getChildren().setAll(new HBox(10, title, position), mediaLine, player,
            times(segment, context, editable, box, state, message), message);
        TagBadges.of(segment, context).ifPresent(tags -> root.getChildren().add(1, tags));
    }

    /** Start, end and duration, with nudges and "set from the player position", or "whole file" and "mark a segment". */
    private static Node times(MediaSegment segment, ViewContext context, boolean editable,
                              Optional<MediaPlayerBox> box, State state, Label message) {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(4);
        if (segment.getRange().isEmpty()) {
            grid.addRow(0, bold("Range:"), new Label("whole file"));
            if (editable && has(segment, MediaSegment.RANGE) && box.isPresent()) {
                Button markStart = new Button("Set start here");
                Button markEnd = new Button("Set end here");
                markEnd.setDisable(state.markedStart == null);
                markStart.setOnAction(e -> box.get().currentTimeMs().ifPresent(ms -> {
                    state.markedStart = ms;
                    markEnd.setDisable(false);
                    message.setText("Start set at " + TimeCodes.display(ms) + ": play on, then set the end");
                    message.setStyle("-fx-text-fill: #777777;");
                }));
                markEnd.setOnAction(e -> box.get().currentTimeMs().ifPresent(ms -> {
                    if (state.markedStart == null) return;
                    long start = Math.min(state.markedStart, ms);
                    long end = Math.max(state.markedStart, ms);
                    if (end <= start) return;
                    state.markedStart = null;
                    show(message, context.setField(segment, MediaSegment.RANGE,
                        TimeCodes.write(new TimeRange(start, end))));
                }));
                HBox mark = new HBox(6, new Label("Mark a segment:"), markStart, markEnd);
                mark.setAlignment(Pos.CENTER_LEFT);
                grid.add(mark, 0, 1, 3, 1);
            }
            return grid;
        }
        TimeRange range = segment.getRange().get();
        grid.addRow(0, bold("Start:"), time(segment, MediaSegment.START, range.startMs(), context, editable, box, message));
        grid.addRow(1, bold("End:"), time(segment, MediaSegment.END, range.endMs(), context, editable, box, message));
        grid.addRow(2, bold("Duration:"), new Label(TimeCodes.display(range.durationMs())));
        if (editable && has(segment, MediaSegment.RANGE) && segment.getMediaReference().isPresent()) {
            Button whole = new Button("Whole file");
            whole.setTooltip(new Tooltip("Remove the range: the marker refers to the whole file"));
            whole.setOnAction(e -> show(message, context.setField(segment, MediaSegment.RANGE, "")));
            grid.add(whole, 1, 3);
        }
        return grid;
    }

    private static Node time(MediaSegment segment, String field, long ms, ViewContext context, boolean editable,
                             Optional<MediaPlayerBox> box, Label message) {
        if (!editable || !has(segment, field)) {
            return new Label(TimeCodes.display(ms));
        }
        TextField value = new TextField(TimeCodes.display(ms));
        value.setPrefColumnCount(9);
        value.setOnAction(e -> show(message, context.setField(segment, field, value.getText())));
        Button minus = new Button("−100 ms");
        minus.setOnAction(e -> show(message, context.setField(segment, field, Long.toString(Math.max(0, ms - NUDGE_MS)))));
        Button plus = new Button("+100 ms");
        plus.setOnAction(e -> show(message, context.setField(segment, field, Long.toString(ms + NUDGE_MS))));
        HBox row = new HBox(4, value, minus, plus);
        row.setAlignment(Pos.CENTER_LEFT);
        box.ifPresent(b -> {
            Button here = new Button("Set from player");
            here.setTooltip(new Tooltip("Use the current position of the player"));
            here.setOnAction(e -> b.currentTimeMs().ifPresent(pos ->
                show(message, context.setField(segment, field, Long.toString(pos)))));
            row.getChildren().add(here);
        });
        return row;
    }

    private static Optional<String> chooseFile(Node owner, MediaSegment segment, ViewContext context,
                                               MediaResolver.Resolution media) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Media file");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio and video", "*.mp3", "*.wav", "*.aif", "*.aiff", "*.m4a",
                "*.aac", "*.mp4", "*.m4v", "*.flv"),
            new FileChooser.ExtensionFilter("All files", "*"));
        Optional<Path> textDirectory = Optional.ofNullable(
            segment.getTextCoordinate().text().path().toAbsolutePath().getParent());
        media.file().path().map(Path::getParent).or(() -> textDirectory)
            .map(Path::toFile).filter(File::isDirectory).ifPresent(chooser::setInitialDirectory);
        File file = chooser.showOpenDialog(owner.getScene() == null ? null : owner.getScene().getWindow());
        return Optional.ofNullable(file)
            .map(f -> MediaPaths.toValue(f.toPath(), context.projectDirectory(), textDirectory));
    }

    private static boolean has(MediaSegment segment, String field) {
        return segment instanceof HasFields h && h.field(field).isPresent();
    }

    private static void show(Label message, EditOutcome outcome) {
        message.setStyle("-fx-text-fill: #c33;");
        message.setText(outcome.isApplied() ? "" : outcome.message());
    }

    private static Label hint(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #888888;");
        return l;
    }

    private static Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }
}
