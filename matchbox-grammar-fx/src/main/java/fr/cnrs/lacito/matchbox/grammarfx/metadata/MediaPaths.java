package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Former place of the resolution of media files, kept for plugins: it delegates to
 * {@link fr.cnrs.lacito.matchbox.corpus.MediaPaths} (text directory first, then project directory).
 *
 * @deprecated use {@link fr.cnrs.lacito.matchbox.corpus.MediaPaths}
 */
@Deprecated
public final class MediaPaths {

    private MediaPaths() {}

    public static boolean isUrl(String value) {
        return fr.cnrs.lacito.matchbox.corpus.MediaPaths.isUrl(value);
    }

    public static fr.cnrs.lacito.matchbox.corpus.MediaPaths.Resolution resolve(
            String value, Optional<Path> projectDirectory, Optional<Path> textDirectory) {
        return fr.cnrs.lacito.matchbox.corpus.MediaPaths.resolve(value, projectDirectory, textDirectory);
    }

    public static String toValue(Path chosen, Optional<Path> projectDirectory, Optional<Path> textDirectory) {
        return fr.cnrs.lacito.matchbox.corpus.MediaPaths.toValue(chosen, projectDirectory, textDirectory);
    }

    public static fr.cnrs.lacito.matchbox.corpus.MediaPaths.Kind kindOf(Path path) {
        return fr.cnrs.lacito.matchbox.corpus.MediaPaths.kindOf(path);
    }
}
