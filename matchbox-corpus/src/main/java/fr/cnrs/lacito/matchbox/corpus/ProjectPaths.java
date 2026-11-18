package fr.cnrs.lacito.matchbox.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Paths relative to a base directory, the same on every system: the paths of a project file
 * relative to the <b>project directory</b> (the directory containing the project file), media
 * references relative to it.
 *
 * <ul>
 * <li>A stored relative path uses {@code /} between its elements on every system; a {@code \} is
 *     also read as a separator (a value written by hand on Windows).</li>
 * <li>"Inside" compares paths element by element (so {@code proj2} is not inside {@code proj}),
 *     normalised and absolute, then, if they differ, their real paths when both exist (macOS
 *     {@code /var} is {@code /private/var}, a symbolic link to the base).</li>
 * </ul>
 */
public final class ProjectPaths {

    private ProjectPaths() {}

    /**
     * The absolute, normalised path of a stored value: relative values are resolved against the
     * base (against the working directory if there is no base).
     *
     * @throws java.nio.file.InvalidPathException if the value is not a path on this system
     */
    public static Path resolve(String stored, Optional<Path> base) {
        Path p = Path.of(separators(stored.strip()));
        if (!p.isAbsolute() && base.isPresent()) p = base.get().resolve(p);
        return p.toAbsolutePath().normalize();
    }

    /** True if the path is the base or below it. */
    public static boolean isInside(Path path, Path base) {
        return relative(path, base).isPresent();
    }

    /** True if there is a base and the path is inside it. */
    public static boolean isInside(Path path, Optional<Path> base) {
        return base.isPresent() && isInside(path, base.get());
    }

    /** The path relative to the base ({@code ""} for the base itself), if it is inside it. */
    public static Optional<Path> relative(Path path, Path base) {
        Path p = path.toAbsolutePath().normalize();
        Path b = base.toAbsolutePath().normalize();
        if (p.startsWith(b)) return Optional.of(b.relativize(p));
        Optional<Path> realP = real(p);
        Optional<Path> realB = real(b);
        if (realP.isPresent() && realB.isPresent() && realP.get().startsWith(realB.get())) {
            return Optional.of(realB.get().relativize(realP.get()));
        }
        return Optional.empty();
    }

    /**
     * What to store for a path: relative to the base with {@code /} separators if inside it
     * ({@code .} for the base itself), otherwise its absolute path.
     */
    public static String toStored(Path path, Optional<Path> base) {
        return base.flatMap(b -> relative(path, b)).map(ProjectPaths::slashes)
            .orElseGet(() -> path.toAbsolutePath().normalize().toString());
    }

    /**
     * How to show a path: relative to the base if inside it (system separators), otherwise its
     * absolute path.
     */
    public static String display(Path path, Optional<Path> base) {
        return base.flatMap(b -> relative(path, b)).map(r -> r.toString().isEmpty() ? "." : r.toString())
            .orElseGet(() -> path.toAbsolutePath().normalize().toString());
    }

    private static String slashes(Path relative) {
        if (relative.toString().isEmpty()) return ".";
        StringBuilder b = new StringBuilder();
        for (Path element : relative) {
            if (!b.isEmpty()) b.append('/');
            b.append(element);
        }
        return b.toString();
    }

    private static String separators(String v) {
        return v.indexOf('\\') >= 0 && java.io.File.separatorChar == '/' ? v.replace('\\', '/') : v;
    }

    private static Optional<Path> real(Path p) {
        if (!Files.exists(p)) return Optional.empty();
        try {
            return Optional.of(p.toRealPath());
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }
}
