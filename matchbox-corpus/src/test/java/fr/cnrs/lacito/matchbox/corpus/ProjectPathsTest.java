package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPathsTest {

    @TempDir
    Path dir;

    @Test
    void pathsInsideAreStoredRelativeWithSlashes() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        Path file = project.resolve("notes").resolve("2025").resolve("a.txt");
        assertEquals("notes/2025/a.txt", ProjectPaths.toStored(file, Optional.of(project)), "'/' on every system");
        assertEquals(".", ProjectPaths.toStored(project, Optional.of(project)), "the base itself");
        assertEquals("a.txt", ProjectPaths.toStored(project.resolve("x").resolve("..").resolve("a.txt"),
            Optional.of(project)), "normalised");
    }

    @Test
    void pathsOutsideAreStoredAbsolute() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        Path sibling = dir.resolve("proj2").resolve("a.txt");
        assertFalse(ProjectPaths.isInside(sibling, project), "proj2 is not inside proj");
        assertEquals(sibling.toAbsolutePath().normalize().toString(), ProjectPaths.toStored(sibling, Optional.of(project)));
        Path parent = dir.resolve("b.txt");
        assertEquals(parent.toAbsolutePath().normalize().toString(), ProjectPaths.toStored(parent, Optional.of(project)),
            "no ../ written");
        assertEquals(parent.toAbsolutePath().normalize().toString(), ProjectPaths.toStored(parent, Optional.empty()),
            "no base: absolute");
        assertFalse(ProjectPaths.isInside(parent, Optional.empty()));
    }

    @Test
    void storedValuesAreResolvedAgainstTheBase() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        Path expected = project.resolve("notes").resolve("a.txt").toAbsolutePath().normalize();
        assertEquals(expected, ProjectPaths.resolve("notes/a.txt", Optional.of(project)));
        assertEquals(expected, ProjectPaths.resolve("notes\\a.txt", Optional.of(project)), "a backslash is a separator");
        assertEquals(expected, ProjectPaths.resolve("./x/../notes/a.txt", Optional.of(project)));
        assertEquals(dir.resolve("b.txt").toAbsolutePath().normalize(),
            ProjectPaths.resolve("../b.txt", Optional.of(project)), "../ is read");
        assertEquals(expected, ProjectPaths.resolve(expected.toString(), Optional.of(dir.resolve("elsewhere"))),
            "absolute values are kept");
        assertEquals(project.toAbsolutePath().normalize(), ProjectPaths.resolve(".", Optional.of(project)));
    }

    @Test
    void displayIsRelativeInsideAndAbsoluteOutside() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        assertEquals(Path.of("corpus", "a.txt").toString(),
            ProjectPaths.display(project.resolve("corpus").resolve("a.txt"), Optional.of(project)));
        assertEquals(".", ProjectPaths.display(project, Optional.of(project)));
        Path out = dir.resolve("other.txt");
        assertEquals(out.toAbsolutePath().normalize().toString(), ProjectPaths.display(out, Optional.of(project)));
    }

    @Test
    void aLinkToTheBaseIsInside() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        Path file = Files.writeString(project.resolve("a.txt"), "a");
        Path link = dir.resolve("link");
        try {
            Files.createSymbolicLink(link, project);
        } catch (Exception e) {
            Assumptions.abort("symbolic links cannot be created here: " + e);
        }
        assertTrue(ProjectPaths.isInside(file, link), "same directory through a link");
        assertEquals("a.txt", ProjectPaths.toStored(link.resolve("a.txt"), Optional.of(project)));
    }
}
