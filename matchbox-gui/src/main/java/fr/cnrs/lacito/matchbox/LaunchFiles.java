package fr.cnrs.lacito.matchbox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Project files Matchbox is asked to open from outside: a double-click on a {@code .mbx} file.
 * <ul>
 * <li>Linux and Windows pass the file as a command-line argument: {@link #projectIn(List)}.</li>
 * <li>macOS sends "open file" events, possibly before the window exists: they are kept until a
 * consumer is set ({@link #setConsumer}), then given to it in order.</li>
 * </ul>
 * No JavaFX here: the consumer decides on which thread a project is opened.
 */
public final class LaunchFiles {

    /** The project file extension. */
    public static final String EXTENSION = ".mbx";

    private final List<Path> pending = new ArrayList<>();
    private Consumer<Path> consumer;

    /** The first argument naming a project file ({@code .mbx}, case ignored), as an absolute path. */
    public static Optional<Path> projectIn(List<String> args) {
        return args.stream()
            .filter(a -> !a.startsWith("-"))
            .filter(LaunchFiles::isProject)
            .findFirst()
            .map(a -> Path.of(a).toAbsolutePath().normalize());
    }

    static boolean isProject(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    /** Files of an "open file" event; those that are not projects are ignored. Any thread. */
    public void offer(List<File> files) {
        List<Path> projects = files.stream()
            .filter(f -> isProject(f.getName()))
            .map(f -> f.toPath().toAbsolutePath().normalize())
            .toList();
        if (projects.isEmpty()) return;
        Consumer<Path> c;
        synchronized (this) {
            c = consumer;
            if (c == null) {
                pending.addAll(projects);
                return;
            }
        }
        projects.forEach(c);
    }

    /** Sets who opens the projects, and gives it those received so far. */
    public void setConsumer(Consumer<Path> consumer) {
        List<Path> received;
        synchronized (this) {
            this.consumer = consumer;
            received = List.copyOf(pending);
            pending.clear();
        }
        received.forEach(consumer);
    }

    /** Takes the projects received so far without a consumer (e.g. the one to open at startup). */
    public synchronized List<Path> drain() {
        List<Path> received = List.copyOf(pending);
        pending.clear();
        return received;
    }
}
