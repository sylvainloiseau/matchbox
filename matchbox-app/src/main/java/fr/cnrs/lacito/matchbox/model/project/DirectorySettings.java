package fr.cnrs.lacito.matchbox.model.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Which files of a directory added to a project belong to the project: those matching an include
 * pattern (every file if there is none) and no exclude pattern ({@link Glob}), in the directory
 * only or in its sub-directories too. Recorded in the project file.
 *
 * @param includes  patterns of the files to include; empty: every file
 * @param excludes  patterns of the files to leave out (they win over the includes)
 * @param recursive include the files of the sub-directories
 */
public record DirectorySettings(List<String> includes, List<String> excludes, boolean recursive) {

    public DirectorySettings {
        includes = clean(includes);
        excludes = clean(excludes);
    }

    /** Defaults of a new directory: one include per extension ({@code *.txt}), none if no extension. */
    public static DirectorySettings of(List<String> fileExtensions, boolean recursive) {
        return new DirectorySettings(fileExtensions.stream().map(e -> "*." + e).toList(), List.of(), recursive);
    }

    private static List<String> clean(List<String> patterns) {
        return patterns == null ? List.of() : patterns.stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    /** Why the patterns are invalid; empty if they are valid. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        Stream.concat(includes.stream(), excludes.stream()).map(Glob::problem)
            .filter(p -> p != null).forEach(problems::add);
        return problems;
    }

    /** A copy with one more exclude pattern (unchanged if it is already there). */
    public DirectorySettings withExclude(String pattern) {
        if (excludes.contains(pattern.strip())) return this;
        List<String> more = new ArrayList<>(excludes);
        more.add(pattern);
        return new DirectorySettings(includes, more, recursive);
    }

    /** True if a file of the directory, given by its relative path ("/" separators), belongs to the project. */
    public boolean accepts(String relativePath) {
        List<Glob> in = includes.stream().map(Glob::of).toList();
        List<Glob> out = excludes.stream().map(Glob::of).toList();
        return accepts(relativePath, in, out);
    }

    private static boolean accepts(String relativePath, List<Glob> in, List<Glob> out) {
        return (in.isEmpty() || in.stream().anyMatch(g -> g.matches(relativePath)))
            && out.stream().noneMatch(g -> g.matches(relativePath));
    }

    /**
     * The files of {@code directory} that belong to the project, sorted.
     *
     * @throws IllegalArgumentException if a pattern is invalid
     */
    public List<Path> scan(Path directory) throws IOException {
        List<Glob> in = includes.stream().map(Glob::of).toList();
        List<Glob> out = excludes.stream().map(Glob::of).toList();
        try (Stream<Path> files = Files.walk(directory, recursive ? Integer.MAX_VALUE : 1)) {
            return files.filter(Files::isRegularFile)
                .filter(f -> accepts(relativePath(directory, f), in, out))
                .sorted()
                .toList();
        }
    }

    /** The path of a file relative to the directory, with "/" separators. */
    public static String relativePath(Path directory, Path file) {
        Path relative = directory.relativize(file);
        List<String> parts = new ArrayList<>();
        relative.forEach(p -> parts.add(p.toString()));
        return String.join("/", parts);
    }
}
