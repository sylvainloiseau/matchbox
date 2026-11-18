package fr.cnrs.lacito.matchbox.corpus;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolution of the media files named in the texts (an {@code @media} file, the {@code media}
 * metadata of a header, a section or an IGT). Pure: no JavaFX.
 *
 * <p>An absolute path (or a {@code file:} URI) is used as is. A relative path is looked for first in
 * the <b>directory of the text file</b>, then, if no file is there, in the <b>project directory</b>
 * (when the project is saved). The same rule holds for every notation: the plugins keep the
 * reference as written.
 */
public final class MediaPaths {

    /** What JavaFX can play. */
    public enum Kind { AUDIO, VIDEO, UNSUPPORTED }

    /** Where a candidate comes from. */
    public enum Base {
        TEXT_DIRECTORY("the text's directory"),
        PROJECT_DIRECTORY("the project directory"),
        ABSOLUTE("absolute path");

        private final String description;

        Base(String description) {
            this.description = description;
        }

        /** "the text's directory", for the messages. */
        public String description() {
            return description;
        }
    }

    /** A file looked for, and the base it was resolved against. */
    public record Candidate(Path path, Base base) {}

    /**
     * @param path       the resolved path: the file found, else the first candidate; empty if the
     *                   value is not a path
     * @param exists     true if the file exists
     * @param candidates the files looked for, in order
     */
    public record Resolution(Optional<Path> path, boolean exists, List<Candidate> candidates) {

        public Resolution {
            candidates = List.copyOf(candidates);
        }

        /** Without the candidates (the form used before they were recorded). */
        public Resolution(Optional<Path> path, boolean exists) {
            this(path, exists, path.map(p -> List.of(new Candidate(p, Base.ABSOLUTE))).orElse(List.of()));
        }

        /** The candidate that exists, if any. */
        public Optional<Candidate> found() {
            return exists ? candidates.stream().filter(c -> path.map(c.path()::equals).orElse(false)).findFirst()
                : Optional.empty();
        }

        /** Nothing to resolve (blank value, URL, not a path). */
        public static Resolution none() {
            return new Resolution(Optional.empty(), false, List.of());
        }
    }

    private static final Set<String> AUDIO = Set.of("mp3", "wav", "aif", "aiff", "m4a", "aac");
    private static final Set<String> VIDEO = Set.of("mp4", "m4v", "flv", "fxm");

    private MediaPaths() {}

    /** True for {@code http://} and {@code https://} values. */
    public static boolean isUrl(String value) {
        String v = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }

    /**
     * The file of a media reference.
     *
     * @param projectDirectory tried second (empty while the project is not saved)
     * @param textDirectory    the directory of the text holding the reference, tried first
     */
    public static Resolution resolve(String value, Optional<Path> projectDirectory, Optional<Path> textDirectory) {
        if (value == null || value.isBlank() || isUrl(value)) {
            return Resolution.none();
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
            return Resolution.none();
        }
        if (path.isAbsolute()) {
            Path p = path.normalize();
            return new Resolution(Optional.of(p), Files.isRegularFile(p), List.of(new Candidate(p, Base.ABSOLUTE)));
        }
        List<Candidate> candidates = new ArrayList<>();
        textDirectory.ifPresent(d -> candidates.add(new Candidate(d.resolve(path).normalize(), Base.TEXT_DIRECTORY)));
        projectDirectory.ifPresent(d -> candidates.add(new Candidate(d.resolve(path).normalize(), Base.PROJECT_DIRECTORY)));
        for (Candidate c : candidates) {
            if (Files.isRegularFile(c.path())) {
                return new Resolution(Optional.of(c.path()), true, candidates);
            }
        }
        return new Resolution(candidates.stream().findFirst().map(Candidate::path), false, candidates);
    }

    /** Backslashes of a value written on Windows are separators on every system. */
    private static String separatorsOf(String v) {
        return v.indexOf('\\') >= 0 && java.io.File.separatorChar == '/' ? v.replace('\\', '/') : v;
    }

    /**
     * The value to write for a chosen file: relative to the text directory if inside it, otherwise
     * relative to the project directory if inside it (with {@code /} separators), otherwise
     * absolute. A relative value is written only if it is read back as the chosen file (a file of
     * the same relative path in the text directory would hide one of the project directory).
     */
    public static String toValue(Path chosen, Optional<Path> projectDirectory, Optional<Path> textDirectory) {
        Path file = chosen.toAbsolutePath().normalize();
        for (Optional<Path> base : List.of(textDirectory, projectDirectory)) {
            if (ProjectPaths.isInside(file, base)) {
                String value = ProjectPaths.toStored(file, base);
                Optional<Path> back = resolve(value, projectDirectory, textDirectory).path();
                if (back.isPresent() && sameFile(back.get(), file)) return value;
            }
        }
        return file.toString();
    }

    private static boolean sameFile(Path a, Path b) {
        if (a.equals(b)) return true;
        try {
            return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Where a file was looked for, for the messages: "notes/rec/a.wav (the text's directory)" for a
     * file found, "looked in notes/rec/a.wav, then rec/a.wav" for a file not found (paths relative
     * to the project directory when inside it); empty when there was nothing to look for.
     */
    public static String describe(Resolution r, Optional<Path> projectDirectory) {
        Optional<Candidate> found = r.found();
        if (found.isPresent()) {
            return ProjectPaths.display(found.get().path(), projectDirectory) + " (" + found.get().base().description() + ")";
        }
        if (r.candidates().isEmpty()) return "";
        List<String> paths = r.candidates().stream().map(c -> ProjectPaths.display(c.path(), projectDirectory)).toList();
        return "looked in " + String.join(", then ", paths);
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
