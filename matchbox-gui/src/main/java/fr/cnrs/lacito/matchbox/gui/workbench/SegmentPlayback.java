package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaPlayers;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaResolver;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MediaPaths;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Plays the media segments for every mode (the ▶ buttons of the editor, "Play media segment"):
 * resolves the file of a segment, plays or pauses it with the window's players, and reports
 * problems on the status line.
 */
public final class SegmentPlayback {

    private final ViewContext context;
    private final StatusLine status;

    public SegmentPlayback(ViewContext context, StatusLine status) {
        this.context = context;
        this.status = status;
    }

    /** Plays a segment, or pauses it if it is playing; problems go to the status line. */
    public void toggle(MediaSegment segment) {
        Optional<Path> file = playableFile(segment);
        if (file.isEmpty()) return;
        MediaPlayers players = context.mediaPlayers();
        if (players.isPlaying(file.get(), segment.getRange())) {
            players.pause();
        } else {
            players.play(file.get(), segment.getRange());
            players.error(file.get()).ifPresent(e -> status.warn("Cannot play " + file.get().getFileName() + ": " + e));
        }
    }

    /** The resolved file of a segment if it can be played; otherwise the reason is reported. */
    private Optional<Path> playableFile(MediaSegment segment) {
        String problem = problem(segment);
        if (problem != null) {
            status.warn(problem);
            return Optional.empty();
        }
        status.clear();
        return MediaResolver.resolve(segment, context.corpus(), context.projectDirectory()).file().path();
    }

    /** Why a segment cannot be played, or null. */
    public String problem(MediaSegment segment) {
        MediaResolver.Resolution r = MediaResolver.resolve(segment, context.corpus(), context.projectDirectory());
        if (r.source() == MediaResolver.Source.NONE) {
            return "No media file: write one after @media, or a \"media:\" entry in the section or the header";
        }
        if (r.isUrl()) return "Remote media are not played";
        if (!r.file().exists()) return "File not found: " + r.file().path().map(Path::toString).orElse("");
        if (MediaPaths.kindOf(r.file().path().get()) == MediaPaths.Kind.UNSUPPORTED) {
            return "Format not supported by the player";
        }
        return null;
    }

    /** True if this segment is the one playing. */
    public boolean isPlaying(MediaSegment segment) {
        MediaPlayers players = context.mediaPlayers();
        if (players.playingProperty().get().isEmpty()) return false;
        return MediaResolver.resolve(segment, context.corpus(), context.projectDirectory()).file().path()
            .map(p -> players.isPlaying(p, segment.getRange()))
            .orElse(false);
    }
}
