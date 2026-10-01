package fr.cnrs.lacito.matchbox.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** How a project reads its text files: charset, and which files of a directory are added. */
public interface DocumentOptions {

    /** Charset used to read (and then save) files opened from now on. */
    Charset charset();

    /** Extensions (lower case, without dot) of the files added from a directory; empty means all files. */
    List<String> fileExtensions();

    /** True if a file found in a directory is added to the project. */
    default boolean acceptsFile(Path file) {
        return hasExtension(file, fileExtensions());
    }

    DocumentOptions DEFAULT = of(StandardCharsets.UTF_8, List.of("txt", "md"));

    static DocumentOptions of(Charset charset, List<String> fileExtensions) {
        List<String> extensions = List.copyOf(fileExtensions);
        return new DocumentOptions() {
            public Charset charset() { return charset; }
            public List<String> fileExtensions() { return extensions; }
        };
    }

    /** Case-insensitive extension test; {@code extensions} must be normalised (lower case, no dot). */
    static boolean hasExtension(Path file, List<String> extensions) {
        if (extensions.isEmpty()) {
            return true;
        }
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
