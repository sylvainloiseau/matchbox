package fr.cnrs.lacito.matchbox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecentItemsTest {

    private static final Path A = Path.of("/data/a.txt");
    private static final Path B = Path.of("/data/b.txt");
    private static final Path C = Path.of("/data/c.txt");

    @Test
    void mostRecentFirstWithoutDuplicates() {
        RecentItems r = new RecentItems(new SimpleIntegerProperty(10));
        r.add(A);
        r.add(B);
        r.add(Path.of("/data/x/../a.txt")); // same as A once normalised
        assertEquals(List.of(A, B), r.items());
    }

    @Test
    void trimmedToMaximumIncludingWhenItShrinks() {
        SimpleIntegerProperty max = new SimpleIntegerProperty(3);
        RecentItems r = new RecentItems(max);
        r.add(A);
        r.add(B);
        r.add(C);
        r.add(Path.of("/data/d.txt"));
        assertEquals(3, r.items().size());
        max.set(1);
        assertEquals(List.of(Path.of("/data/d.txt")), r.items());
    }

    @Test
    void oneChangeEventPerOperation() {
        RecentItems r = new RecentItems(new SimpleIntegerProperty(10));
        r.add(A);
        List<String> events = new ArrayList<>();
        r.items().addListener((ListChangeListener<Path>) c -> events.add("change"));
        r.add(B);
        r.add(A);
        assertEquals(2, events.size());
    }

    @Test
    void existingFiltersMissingFiles(@TempDir Path dir) throws Exception {
        Path present = Files.createFile(dir.resolve("present.txt"));
        RecentItems r = new RecentItems(new SimpleIntegerProperty(10));
        r.add(dir.resolve("missing.txt"));
        r.add(present);
        assertEquals(List.of(present), r.existing());
        assertEquals(2, r.items().size());
    }
}
