package fr.cnrs.lacito.matchbox;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LaunchFilesTest {

    @Test
    void noArgumentGivesNoProject() {
        assertEquals(Optional.empty(), LaunchFiles.projectIn(List.of()));
    }

    @Test
    void theFirstProjectArgumentIsChosen() {
        Optional<Path> p = LaunchFiles.projectIn(List.of("--verbose", "notes.txt", "Field.MBX", "other.mbx"));
        assertEquals(Optional.of(Path.of("Field.MBX").toAbsolutePath().normalize()), p);
    }

    @Test
    void optionsAreNotFiles() {
        assertEquals(Optional.empty(), LaunchFiles.projectIn(List.of("--config=a.mbx")));
    }

    @Test
    void eventsBeforeTheConsumerAreKeptThenGivenInOrder() {
        LaunchFiles files = new LaunchFiles();
        files.offer(List.of(new File("a.mbx"), new File("notes.txt")));
        files.offer(List.of(new File("b.mbx")));
        List<Path> opened = new ArrayList<>();
        files.setConsumer(opened::add);
        assertEquals(List.of(Path.of("a.mbx").toAbsolutePath(), Path.of("b.mbx").toAbsolutePath()), opened);
        files.offer(List.of(new File("c.mbx")));
        assertEquals(Path.of("c.mbx").toAbsolutePath(), opened.getLast());
        assertEquals(List.of(), files.drain());
    }

    @Test
    void drainTakesTheEventsReceivedAtStartup() {
        LaunchFiles files = new LaunchFiles();
        files.offer(List.of(new File("a.mbx")));
        assertEquals(List.of(Path.of("a.mbx").toAbsolutePath()), files.drain());
        assertEquals(List.of(), files.drain());
    }
}
