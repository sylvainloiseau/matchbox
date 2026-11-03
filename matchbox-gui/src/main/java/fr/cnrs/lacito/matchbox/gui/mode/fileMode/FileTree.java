package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.FileOrder;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The files of a project as File Mode's tree shows them: one top node per entry of the project
 * definition, in order; a directory groups the files it gives, with its sub-directories as nodes
 * (directories first, then files, each sorted by name with {@link FileOrder#NAMES}); files added alone are top nodes, never
 * grouped. Sub-directories without file are not shown. Pure: no JavaFX control.
 */
public final class FileTree {

    /** A node of the tree. */
    public sealed interface Node permits TopDirectory, SubDirectory, FileNode {
        /** Identity of the node across rebuilds (expanded state, selection). */
        Path path();

        String name();
    }

    /** A directory added to the project. */
    public record TopDirectory(Path path) implements Node {
        public String name() {
            return path.toString();
        }
    }

    /** A sub-directory of a directory added to the project. */
    public record SubDirectory(Path top, Path path) implements Node {
        public String name() {
            return path.getFileName().toString();
        }
    }

    /**
     * A file.
     *
     * @param directory the directory of the project it comes from; empty for a file added alone
     */
    public record FileNode(TextDocument document, Optional<Path> directory) implements Node {
        public Path path() {
            return document.getFilePath();
        }

        public String name() {
            Path name = document.getFilePath().getFileName();
            return name == null ? document.getFilePath().toString() : name.toString();
        }
    }

    /** A node and its children. */
    public record Entry(Node node, List<Entry> children) {
        public Entry {
            children = List.copyOf(children);
        }

        /** The documents of the node and below. */
        public Stream<TextDocument> documents() {
            return node instanceof FileNode f ? Stream.of(f.document())
                : children.stream().flatMap(Entry::documents);
        }

        public long fileCount() {
            return documents().count();
        }
    }

    private FileTree() {
    }

    /**
     * @param filter only the files whose path contains it (ignoring case) and their directories;
     *               empty or null: every file
     */
    public static List<Entry> build(Project project, String filter) {
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        Map<Path, TextDocument> documents = new HashMap<>();
        project.getDerivedFileList().forEach(d -> documents.put(d.getFilePath(), d));
        Set<Path> shown = new LinkedHashSet<>();
        List<Entry> top = new ArrayList<>();
        for (Path entry : project.getDefinitionPaths()) {
            if (project.directorySettings(entry).isPresent()) {
                Folder folder = new Folder(entry);
                for (Path file : project.filesOf(entry)) {
                    TextDocument d = documents.get(file);
                    if (d == null) continue;
                    shown.add(file);
                    if (matches(d, f)) folder.add(entry.relativize(file), d, entry);
                }
                if (f.isEmpty() || !folder.isEmpty()) top.add(folder.toEntry(new TopDirectory(entry), entry));
            } else {
                TextDocument d = documents.get(entry);
                if (d == null) continue;
                shown.add(entry);
                if (matches(d, f)) top.add(new Entry(new FileNode(d, Optional.empty()), List.of()));
            }
        }
        // documents outside the definition (should not happen): shown rather than lost
        for (TextDocument d : project.getDerivedFileList()) {
            if (!shown.contains(d.getFilePath()) && matches(d, f)) {
                top.add(new Entry(new FileNode(d, Optional.empty()), List.of()));
            }
        }
        return top;
    }

    private static boolean matches(TextDocument d, String filter) {
        return filter.isEmpty() || d.getFilePath().toString().toLowerCase(Locale.ROOT).contains(filter);
    }

    /** A directory being built: its sub-directories by name and its files. */
    private static final class Folder {
        private final Path path;
        private final Map<String, Folder> subdirectories = new TreeMap<>(FileOrder.NAMES);
        private final List<TextDocument> files = new ArrayList<>();

        Folder(Path path) {
            this.path = path;
        }

        void add(Path relative, TextDocument d, Path top) {
            if (relative.getNameCount() == 1) {
                files.add(d);
            } else {
                String name = relative.getName(0).toString();
                subdirectories.computeIfAbsent(name, n -> new Folder(path.resolve(n)))
                    .add(relative.subpath(1, relative.getNameCount()), d, top);
            }
        }

        boolean isEmpty() {
            return files.isEmpty() && subdirectories.values().stream().allMatch(Folder::isEmpty);
        }

        Entry toEntry(Node node, Path top) {
            List<Entry> children = new ArrayList<>();
            for (Folder sub : subdirectories.values()) {
                if (!sub.isEmpty()) children.add(sub.toEntry(new SubDirectory(top, sub.path), top));
            }
            files.stream()
                .sorted(Comparator.comparing((TextDocument d) -> d.getFilePath().getFileName().toString(),
                    FileOrder.NAMES))
                .forEach(d -> children.add(new Entry(new FileNode(d, Optional.of(top)), List.of())));
            return new Entry(node, children);
        }
    }
}
