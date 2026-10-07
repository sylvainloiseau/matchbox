package fr.cnrs.lacito.matchbox.grammarfx.media;

import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import java.nio.file.Path;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/** {@link MediaPlayers} with JavaFX media players, at most {@value #CAPACITY} files open. Used on the JavaFX thread. */
public final class SharedMediaPlayers implements MediaPlayers {

    static final int CAPACITY = 8;

    private static SharedMediaPlayers fallback;

    private final ReadOnlyObjectWrapper<Optional<Playback>> playing = new ReadOnlyObjectWrapper<>(Optional.empty());
    private final PlayerCache<MediaPlayer> cache = new PlayerCache<>(CAPACITY, this::open, MediaPlayer::dispose);
    private MediaPlayer current;

    /** An instance for views used outside the application (no shared lifetime). */
    public static synchronized MediaPlayers fallback() {
        if (fallback == null) fallback = new SharedMediaPlayers();
        return fallback;
    }

    private MediaPlayer open(Path file) {
        MediaPlayer player = new MediaPlayer(new Media(file.toUri().toString()));
        player.setOnEndOfMedia(() -> {
            player.pause();
            if (player == current) playing.set(Optional.empty());
        });
        player.statusProperty().addListener((obs, o, status) -> {
            if (player == current && status != MediaPlayer.Status.PLAYING && status != MediaPlayer.Status.READY
                && status != MediaPlayer.Status.UNKNOWN && status != MediaPlayer.Status.STALLED) {
                playing.set(Optional.empty());
            }
        });
        return player;
    }

    @Override
    public Optional<MediaPlayer> player(Path file) {
        return cache.get(file);
    }

    @Override
    public Optional<String> error(Path file) {
        return cache.error(file);
    }

    @Override
    public void play(Path file, Optional<TimeRange> range) {
        Path key = file.toAbsolutePath().normalize();
        Optional<MediaPlayer> found = cache.get(key);
        if (found.isEmpty()) return;
        MediaPlayer player = found.get();
        if (current != null && current != player) {
            current.pause();
        }
        current = player;
        cache.setInUse(key);
        playing.set(Optional.of(new Playback(key, range)));
        Runnable start = () -> {
            Duration total = player.getMedia().getDuration();
            Duration from = range.map(r -> Duration.millis(r.startMs())).orElse(Duration.ZERO);
            Duration to = range.map(r -> Duration.millis(r.endMs())).orElse(total);
            player.setStartTime(from);
            if (to != null && !to.isUnknown()) player.setStopTime(to);
            player.seek(from);
            player.play();
        };
        if (player.getStatus() == MediaPlayer.Status.UNKNOWN) {
            player.setOnReady(start);
        } else {
            start.run();
        }
    }

    @Override
    public void pause() {
        if (current != null) current.pause();
        playing.set(Optional.empty());
    }

    @Override
    public ReadOnlyObjectProperty<Optional<Playback>> playingProperty() {
        return playing.getReadOnlyProperty();
    }

    @Override
    public void closeAll() {
        playing.set(Optional.empty());
        current = null;
        cache.closeAll();
    }
}
