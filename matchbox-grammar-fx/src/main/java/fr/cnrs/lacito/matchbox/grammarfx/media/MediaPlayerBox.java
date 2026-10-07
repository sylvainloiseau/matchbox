package fr.cnrs.lacito.matchbox.grammarfx.media;

import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MediaPaths;
import java.nio.file.Path;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

/**
 * Play/pause, position slider and time of a media file or of a range of it; a video is shown above
 * the controls. The player is shared ({@link MediaPlayers}): the box only follows it, and removes
 * its listeners when it leaves the scene. No autoplay.
 */
public final class MediaPlayerBox extends VBox {

    private static final double VIDEO_WIDTH = 360;

    private final Path file;
    private final Optional<TimeRange> range;
    private final MediaPlayers players;
    private final Button play = new Button("▶");
    private final Slider position = new Slider(0, 1, 0);
    private final Label time = new Label("");
    private final Label message = new Label();
    private MediaPlayer player;
    private final ChangeListener<Duration> timeListener = (obs, o, now) -> {
        // the player is shared: follow it only while it plays this file and range
        if (isMine()) showTime(now);
    };
    private final ChangeListener<Optional<MediaPlayers.Playback>> playingListener = (obs, o, n) -> showState();
    private boolean listening;

    public MediaPlayerBox(Path file, Optional<TimeRange> range, MediaPlayers players) {
        this.file = file.toAbsolutePath().normalize();
        this.range = range;
        this.players = players;
        setSpacing(4);
        message.setStyle("-fx-text-fill: #c33;");
        message.setWrapText(true);
        Optional<MediaPlayer> found = players.player(this.file);
        if (found.isEmpty()) {
            message.setText("Cannot play this file" + players.error(this.file).map(e -> ": " + e).orElse(""));
            getChildren().add(message);
            return;
        }
        player = found.get();
        position.setPrefWidth(180);
        range.ifPresent(r -> {
            position.setMin(r.startMs() / 1000.0);
            position.setMax(r.endMs() / 1000.0);
            position.setValue(r.startMs() / 1000.0);
        });
        if (range.isEmpty()) {
            // known once the media is loaded (the service uses setOnReady: listen to the duration instead)
            Runnable setMax = () -> {
                Duration d = player.getMedia().getDuration();
                if (d != null && !d.isUnknown()) position.setMax(Math.max(1, d.toSeconds()));
            };
            player.getMedia().durationProperty().addListener((obs, o, n) -> setMax.run());
            setMax.run();
        }
        position.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing && isMine()) player.seek(Duration.seconds(position.getValue()));
        });
        position.setOnMouseReleased(e -> {
            if (isMine()) player.seek(Duration.seconds(position.getValue()));
        });
        play.setOnAction(e -> {
            if (isMine()) players.pause();
            else players.play(this.file, range);
        });
        if (MediaPaths.kindOf(this.file) == MediaPaths.Kind.VIDEO) {
            MediaView view = new MediaView(player);
            view.setFitWidth(VIDEO_WIDTH);
            view.setPreserveRatio(true);
            getChildren().add(view);
        }
        getChildren().addAll(new HBox(6, play, position, time), message);
        player.setOnError(() -> message.setText("Cannot play this file: "
            + (player.getError() == null ? "" : player.getError().getMessage())));
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                listen();
            } else {
                // deferred: a box moved within its view stays attached
                Platform.runLater(() -> {
                    if (getScene() == null) unlisten();
                });
            }
        });
        listen();
        showState();
        showTime(range.map(r -> Duration.millis(r.startMs())).orElse(Duration.ZERO));
    }

    /** Current position of the player in milliseconds, if this box's file is open. */
    public Optional<Long> currentTimeMs() {
        return player == null ? Optional.empty() : Optional.of((long) player.getCurrentTime().toMillis());
    }

    /** True if the shared player is playing this file and range. */
    public boolean isMine() {
        return players.isPlaying(file, range);
    }

    private void listen() {
        if (listening || player == null) return;
        player.currentTimeProperty().addListener(timeListener);
        players.playingProperty().addListener(playingListener);
        listening = true;
    }

    private void unlisten() {
        if (!listening || player == null) return;
        player.currentTimeProperty().removeListener(timeListener);
        players.playingProperty().removeListener(playingListener);
        listening = false;
    }

    private void showState() {
        play.setText(isMine() ? "⏸" : "▶");
    }

    private void showTime(Duration now) {
        if (!position.isValueChanging()) position.setValue(now.toSeconds());
        long from = range.map(TimeRange::startMs).orElse(0L);
        long relative = Math.max(0, (long) now.toMillis() - from);
        long length = range.map(TimeRange::durationMs)
            .orElse(player == null || player.getMedia().getDuration() == null || player.getMedia().getDuration().isUnknown()
                ? 0L : (long) player.getMedia().getDuration().toMillis());
        time.setText(format(relative) + (length > 0 ? " / " + format(length) : ""));
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
