package fr.cnrs.lacito.matchbox.grammarfx.media;

import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import java.nio.file.Path;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.media.MediaPlayer;

/**
 * Players of media files, shared by all views: each file is opened once and its player reused by
 * every view that plays it; one plays at a time. Given to views by
 * {@code ViewContext.mediaPlayers()}; the application closes the players when the project changes.
 */
public interface MediaPlayers {

    /** The player of a file, opened on first use; the same instance for every caller. */
    Optional<MediaPlayer> player(Path file);

    /** Why a file cannot be played, if it cannot. */
    Optional<String> error(Path file);

    /** Plays a range of a file, or the whole file (empty range), pausing any other player. */
    void play(Path file, Optional<TimeRange> range);

    /** Pauses what is playing. */
    void pause();

    /** What is playing, for the views and the editor to show their state; empty when nothing plays. */
    ReadOnlyObjectProperty<Optional<Playback>> playingProperty();

    /** True if this file and range are playing. */
    default boolean isPlaying(Path file, Optional<TimeRange> range) {
        return playingProperty().get()
            .map(p -> p.file().equals(file.toAbsolutePath().normalize()) && p.range().equals(range))
            .orElse(false);
    }

    /** Closes every player (project closed, application stopped). */
    void closeAll();

    /**
     * @param file  the file playing (absolute, normalised)
     * @param range the range playing; empty for the whole file
     */
    record Playback(Path file, Optional<TimeRange> range) {}
}
