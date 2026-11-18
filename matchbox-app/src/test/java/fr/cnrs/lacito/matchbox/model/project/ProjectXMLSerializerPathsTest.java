package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Paths of a project file relative to the project directory (the directory containing the project file). */
class ProjectXMLSerializerPathsTest {

    @TempDir
    Path dir;

    private static Path abs(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private Path folder(Path base) throws Exception {
        Path notes = Files.createDirectories(base.resolve("notes"));
        Files.writeString(notes.resolve("a.txt"), "aaa");
        Path corpus = Files.createDirectories(base.resolve("corpus"));
        Files.writeString(corpus.resolve("c.txt"), "ccc");
        Files.writeString(base.resolve("dictionary.lift"), "<lift/>");
        Path mbx = base.resolve("project.mbx");
        Files.writeString(mbx, """
            <project name="Moved">
              <fileset>
                <file path="notes/a.txt"/>
                <directory path="corpus" recursive="false"><include>*.txt</include></directory>
              </fileset>
              <inheritedContext><dictionary path="dictionary.lift"/></inheritedContext>
            </project>
            """, StandardCharsets.UTF_8);
        return mbx;
    }

    @Test
    void relativePathsAreResolvedAgainstTheProjectDirectoryWhereverItIs() throws Exception {
        for (String place : List.of("here", "elsewhere")) {
            Path base = Files.createDirectories(dir.resolve(place));
            Project p = ProjectXMLSerializer.load(folder(base).toFile(), List.of());
            assertTrue(p.getLoadProblems().isEmpty(), p.getLoadProblems()::toString);
            assertEquals(Optional.of(abs(base)), p.directory());
            assertEquals(List.of(abs(base.resolve("notes/a.txt")), abs(base.resolve("corpus"))), p.getDefinitionPaths());
            assertEquals(List.of(abs(base.resolve("corpus/c.txt"))), p.filesOf(abs(base.resolve("corpus"))));
            assertEquals(Optional.of(abs(base.resolve("dictionary.lift"))), p.getContextDictionary());
            assertFalse(p.definitionChangedProperty().get(), "loading changes nothing");
        }
    }

    @Test
    void pathsInsideAreWrittenRelativeAndOthersAbsolute() throws Exception {
        Path base = Files.createDirectories(dir.resolve("proj"));
        Path mbx = folder(base);
        Path outside = Files.writeString(dir.resolve("outside.txt"), "ooo");
        Project p = ProjectXMLSerializer.load(mbx.toFile(), List.of());
        p.addFile(outside);
        p.setContextCorpus(base.resolve("context.mbx"));
        ProjectXMLSerializer.save(p);
        String xml = Files.readString(mbx, StandardCharsets.UTF_8);
        assertTrue(xml.contains("path=\"notes/a.txt\""), xml);
        assertTrue(xml.contains("path=\"corpus\""), xml);
        assertTrue(xml.contains("path=\"dictionary.lift\""), xml);
        assertTrue(xml.contains("path=\"context.mbx\""), xml);
        assertTrue(xml.contains("path=\"" + abs(outside) + "\""), "outside: absolute\n" + xml);
    }

    @Test
    void absolutePathsInsideBecomeRelativeAtTheNextSave() throws Exception {
        Path base = Files.createDirectories(dir.resolve("proj"));
        Files.writeString(base.resolve("a.txt"), "aaa");
        Path mbx = base.resolve("project.mbx");
        Files.writeString(mbx, "<project><fileset><file path=\"" + abs(base.resolve("a.txt")) + "\"/></fileset></project>");
        Project p = ProjectXMLSerializer.load(mbx.toFile(), List.of());
        assertFalse(p.definitionChangedProperty().get());
        ProjectXMLSerializer.save(p);
        assertTrue(Files.readString(mbx).contains("path=\"a.txt\""));
    }

    @Test
    void savingInAnotherDirectoryWritesPathsRelativeToIt() throws Exception {
        Path base = Files.createDirectories(dir.resolve("proj"));
        Project p = ProjectXMLSerializer.load(folder(base).toFile(), List.of());
        Path parentFile = dir.resolve("parent.mbx");
        p.setFile(parentFile.toFile());
        assertEquals(Optional.of(abs(dir)), p.directory(), "follows the project file");
        ProjectXMLSerializer.save(p);
        String xml = Files.readString(parentFile);
        assertTrue(xml.contains("path=\"proj/notes/a.txt\""), xml);
        assertTrue(xml.contains("path=\"proj/corpus\""), xml);

        Path other = Files.createDirectories(dir.resolve("other")).resolve("other.mbx");
        p.setFile(other.toFile());
        ProjectXMLSerializer.save(p);
        assertTrue(Files.readString(other).contains("path=\"" + abs(base.resolve("notes/a.txt")) + "\""),
            "not inside the new directory: absolute");
    }

    @Test
    void aMissingRelativeFileIsReportedWithItsResolvedPath() throws Exception {
        Path base = Files.createDirectories(dir.resolve("proj"));
        Path mbx = base.resolve("project.mbx");
        Files.writeString(mbx, "<project><fileset><file path=\"gone/x.txt\"/></fileset></project>");
        Project p = ProjectXMLSerializer.load(mbx.toFile(), List.of());
        assertEquals(1, p.getLoadProblems().size());
        assertTrue(p.getLoadProblems().get(0).contains(abs(base.resolve("gone/x.txt")).toString()),
            p.getLoadProblems()::toString);
    }

    @Test
    void anUnsavedProjectHasNoDirectory() {
        Project p = Project.createDefaultProject(List.of());
        assertEquals(Optional.empty(), p.directory());
        assertFalse(p.isInsideDirectory(dir.resolve("a.txt")));
    }
}
