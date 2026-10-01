package fr.cnrs.lacito.matchbox.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.IntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Bounded most-recently-used list of paths, most recent first. Paths are stored absolute and
 * normalised, without duplicates. Each change produces a single list change event.
 */
public final class RecentItems {

    private final ObservableList<Path> items = FXCollections.observableArrayList();
    private final ObservableList<Path> readOnly = FXCollections.unmodifiableObservableList(items);
    private final IntegerProperty max;

    RecentItems(IntegerProperty max) {
        this.max = max;
        max.addListener((obs, oldMax, newMax) -> setAll(items));
    }

    /** The paths, most recent first (read-only, observable). */
    public ObservableList<Path> items() {
        return readOnly;
    }

    /** Paths that still exist, for menus (missing ones are kept: a drive may be unplugged). */
    public List<Path> existing() {
        return items.stream().filter(Files::exists).toList();
    }

    /** Moves the path to the top of the list, adding it if needed. */
    public void add(Path path) {
        Path p = normalize(path);
        List<Path> list = new ArrayList<>(items);
        list.remove(p);
        list.add(0, p);
        setAll(list);
    }

    public void remove(Path path) {
        List<Path> list = new ArrayList<>(items);
        if (list.remove(normalize(path))) {
            setAll(list);
        }
    }

    public void clear() {
        items.clear();
    }

    void setAll(List<Path> paths) {
        List<Path> list = new ArrayList<>();
        for (Path p : paths) {
            Path n = normalize(p);
            if (!list.contains(n)) {
                list.add(n);
            }
        }
        List<Path> trimmed = list.subList(0, Math.min(list.size(), Math.max(1, max.get())));
        if (!trimmed.equals(items)) {
            items.setAll(List.copyOf(trimmed));
        }
    }

    void setAllFromStrings(List<String> paths) {
        List<Path> list = new ArrayList<>();
        for (String s : paths) {
            try {
                list.add(Path.of(s));
            } catch (RuntimeException e) {
                // invalid path string (e.g. written on another OS): dropped
            }
        }
        setAll(list);
    }

    List<String> toStrings() {
        return items.stream().map(Path::toString).toList();
    }

    private static Path normalize(Path p) {
        return p.toAbsolutePath().normalize();
    }
}
