package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolution of the media files named in metadata values. No JavaFX.
 *
 * <p>An absolute path (or a {@code file:} URI) is used as is; a relative path is resolved first
 * against the project directory (if the project is saved), then, if the file does not exist there,
 * against the directory of the text file.
 */
public final class MediaPaths {

    /** What JavaFX can play. */
    public enum Kind { AUDIO, VIDEO, UNSUPPORTED }

    /**
     * @param path   the resolved path (the first candidate when the file is not found); empty if
     *               the value is not a path
     * @param exists true if the file exists
     */
    public record Resolution(Optional<Path> path, boolean exists) {}

    private static final Set<String> AUDIO = Set.of("mp3", "wav", "aif", "aiff", "m4a", "aac");
    private static final Set<String> VIDEO = Set.of("mp4", "m4v", "flv", "fxm");

    private MediaPaths() {}

    /** True for {@code http://} and {@code https://} values. */
    public static boolean isUrl(String value) {
        String v = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }

    public static Resolution resolve(String value, Optional<Path> projectDirectory, Optional<Path> textDirectory) {
        if (value == null || value.isBlank() || isUrl(value)) {
            return new Resolution(Optional.empty(), false);
        }
        String v = value.strip();
        Path path;
        try {
            if (v.regionMatches(true, 0, "file:", 0, 5)) {
                path = Path.of(URI.create(v));
            } else {
                path = Path.of(separatorsOf(v));
            }
        } catch (IllegalArgumentException e) { // InvalidPathException included
            return new Resolution(Optional.empty(), false);
        }
        if (path.isAbsolute()) {
            Path p = path.normalize();
            return new Resolution(Optional.of(p), Files.isRegularFile(p));
        }
        Path first = null;
        for (Optional<Path> base : java.util.List.of(projectDirectory, textDirectory)) {
            if (base.isEmpty()) continue;
            Path candidate = base.get().resolve(path).normalize();
            if (Files.isRegularFile(candidate)) {
                return new Resolution(Optional.of(candidate), true);
            }
            if (first == null) first = candidate;
        }
        return new Resolution(Optional.ofNullable(first), false);
    }

    /** Backslashes of a value written on Windows are separators on every system. */
    private static String separatorsOf(String v) {
        return v.indexOf('\\') >= 0 && java.io.File.separatorChar == '/' ? v.replace('\\', '/') : v;
    }

    /**
     * The value to write for a chosen file: relative to the project directory if inside it,
     * otherwise relative to the text directory if inside it (with {@code /} separators), otherwise
     * absolute.
     */
    public static String toValue(Path chosen, Optional<Path> projectDirectory, Optional<Path> textDirectory) {
        Path file = chosen.toAbsolutePath().normalize();
        for (Optional<Path> base : java.util.List.of(projectDirectory, textDirectory)) {
            if (base.isPresent()) {
                Path b = base.get().toAbsolutePath().normalize();
                if (file.startsWith(b)) {
                    return b.relativize(file).toString().replace('\\', '/');
                }
            }
        }
        return file.toString();
    }

    public static Kind kindOf(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (AUDIO.contains(ext)) return Kind.AUDIO;
        if (VIDEO.contains(ext)) return Kind.VIDEO;
        return Kind.UNSUPPORTED;
    }
}
