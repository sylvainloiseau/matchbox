package fr.cnrs.lacito.matchbox.corpus;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Identity of a text, known before it is parsed: the application creates it and gives it to the
 * grammar, and every {@link TextCoordinate} of the text refers to it.
 *
 * <p>The application creates ids with {@link #of(Path)}, so that two spellings of the same file
 * give equal ids; the canonical constructor keeps the path as given (useful in tests).
 *
 * @param path the file of the text
 */
public record TextId(Path path) implements Comparable<TextId> {

    public TextId {
        Objects.requireNonNull(path, "path");
    }

    /** Id of a file, with an absolute and normalised path. */
    public static TextId of(Path path) {
        return new TextId(path.toAbsolutePath().normalize());
    }

    /** File name, for display. */
    public String displayName() {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    @Override
    public int compareTo(TextId other) {
        return path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
