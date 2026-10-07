package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.nio.file.Path;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

/**
 * A small audio/video player: play/pause, position slider, time; a video is shown above the
 * controls. The player is released when the box leaves the scene. No autoplay.
 */
final class MediaPlayerBox extends VBox {

    private static final double VIDEO_WIDTH = 360;
    private MediaPlayer player;

    MediaPlayerBox(Path file) {
        setSpacing(4);
        Label message = new Label();
        message.setStyle("-fx-text-fill: #c33;");
        try {
            Media media = new Media(file.toUri().toString());
            player = new MediaPlayer(media);
        } catch (MediaException | IllegalArgumentException | UnsupportedOperationException e) {
            message.setText("Cannot play this file: " + e.getMessage());
            getChildren().add(message);
            return;
        }
        Button play = new Button("▶");
        Slider position = new Slider(0, 1, 0);
        position.setPrefWidth(160);
        Label time = new Label("0:00");
        player.setOnError(() -> message.setText("Cannot play this file: "
            + (player.getError() == null ? "" : player.getError().getMessage())));
        player.setOnReady(() -> position.setMax(Math.max(1, player.getTotalDuration().toSeconds())));
        player.currentTimeProperty().addListener((obs, o, now) -> {
            if (!position.isValueChanging()) position.setValue(now.toSeconds());
            time.setText(format(now) + " / " + format(player.getTotalDuration()));
        });
        position.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) player.seek(Duration.seconds(position.getValue()));
        });
        position.setOnMouseReleased(e -> player.seek(Duration.seconds(position.getValue())));
        player.statusProperty().addListener((obs, o, status) ->
            play.setText(status == MediaPlayer.Status.PLAYING ? "⏸" : "▶"));
        player.setOnEndOfMedia(() -> {
            player.stop();
            player.seek(Duration.ZERO);
        });
        play.setOnAction(e -> {
            if (player.getStatus() == MediaPlayer.Status.PLAYING) player.pause();
            else player.play();
        });
        if (MediaPaths.kindOf(file) == MediaPaths.Kind.VIDEO) {
            MediaView view = new MediaView(player);
            view.setFitWidth(VIDEO_WIDTH);
            view.setPreserveRatio(true);
            getChildren().add(view);
        }
        getChildren().addAll(new HBox(6, play, position, time), message);
        // released when the view is closed (deferred: a row moved within the pane keeps its player)
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                Platform.runLater(() -> {
                    if (getScene() == null) dispose();
                });
            }
        });
    }

    void dispose() {
        if (player != null) {
            player.dispose();
            player = null;
        }
    }

    private static String format(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) return "–";
        long s = (long) d.toSeconds();
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
