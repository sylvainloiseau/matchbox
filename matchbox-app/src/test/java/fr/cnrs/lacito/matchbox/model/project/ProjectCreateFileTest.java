package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link Project#createFile}: an empty file on the disk, added alone or among its directory's files. */
class ProjectCreateFileTest {

    @TempDir
    Path dir;

    private static Project project() {
        return Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
    }

    private static List<String> names(Path root, List<Path> paths) {
        return paths.stream().map(p -> root.relativize(p).toString().replace('\\', '/')).toList();
    }

    @Test
    void createsAnEmptyFileAddedAlone() throws Exception {
        Project project = project();
        Path file = dir.resolve("new.txt");
        Project.Created created = project.createFile(file);
        assertEquals(file.toAbsolutePath().normalize(), created.file());
        assertEquals(Optional.empty(), created.directory());
        assertTrue(Files.isRegularFile(file));
        assertEquals(0, Files.size(file));
        assertEquals(List.of(created.file()), project.getDefinitionPaths());
        assertEquals(1, project.getDerivedFileList().size());
        assertEquals("", project.getDerivedFileList().get(0).getCurrentText());
        assertTrue(project.definitionChangedProperty().get());
    }

    @Test
    void aFileOfARecursiveDirectoryJoinsItsFilesInPathOrder() throws Exception {
        Path notes = Files.createDirectories(dir.resolve("notes"));
        Files.writeString(notes.resolve("a.txt"), "a");
        Files.createDirectories(notes.resolve("sub"));
        Files.writeString(notes.resolve("sub/c.txt"), "c");
        Project project = project();
        project.addDirectory(notes, DirectorySettings.of(List.of("txt"), true));
        Project.Created created = project.createFile(notes.resolve("B.txt"));
        Path directory = notes.toAbsolutePath().normalize();
        assertEquals(Optional.of(directory), created.directory());
        // no <file> entry: the definition still holds the directory only
        assertEquals(List.of(directory), project.getDefinitionPaths());
        // explicit order: case ignored (a < B), files and sub-directories together
        assertEquals(List.of("a.txt", "B.txt", "sub/c.txt"), names(directory, project.filesOf(directory)));
        assertEquals(Optional.of(directory), project.directoryOf(created.file()));
        // a rescan finds the same files, nothing added or removed
        Project.DirectoryChange change = project.rescanDirectory(directory);
        assertEquals(List.of(), change.added());
        assertEquals(List.of(), change.removed());
    }

    @Test
    void aFileInASubDirectoryOfANonRecursiveDirectoryIsAddedAlone() throws Exception {
        Path notes = Files.createDirectories(dir.resolve("notes"));
        Path sub = Files.createDirectories(notes.resolve("sub"));
        Project project = project();
        project.addDirectory(notes, DirectorySettings.of(List.of("txt"), false));
        assertEquals(Optional.empty(), project.owningDirectory(sub.resolve("x.txt")));
        assertEquals(Optional.of(notes.toAbsolutePath().normalize()), project.owningDirectory(notes.resolve("x.txt")));
        Project.Created created = project.createFile(sub.resolve("x.txt"));
        assertEquals(Optional.empty(), created.directory());
        assertTrue(project.getDefinitionPaths().contains(created.file()));
    }

    @Test
    void aFileTheGlobsRefuseIsAddedAlone() throws Exception {
        Path notes = Files.createDirectories(dir.resolve("notes"));
        Project project = project();
        project.addDirectory(notes, DirectorySettings.of(List.of("txt"), true).withExclude("drafts/**"));
        Files.createDirectories(notes.resolve("drafts"));
        assertEquals(Optional.empty(), project.owningDirectory(notes.resolve("drafts/x.txt")));
        assertEquals(Optional.empty(), project.owningDirectory(notes.resolve("x.md")));
        assertEquals(Optional.empty(), project.owningDirectory(dir.resolve("x.txt")));
        assertEquals(Optional.empty(), project.createFile(notes.resolve("x.md")).directory());
    }

    @Test
    void anExistingFileIsNeverWritten() throws Exception {
        Path file = Files.writeString(dir.resolve("old.txt"), "keep me", StandardCharsets.UTF_8);
        Project project = project();
        assertThrows(FileAlreadyExistsException.class, () -> project.createFile(file));
        assertEquals("keep me", Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(project.getDerivedFileList().isEmpty());
        assertFalse(project.definitionChangedProperty().get());
    }

    @Test
    void aFileAlreadyInTheProjectIsRefused() throws Exception {
        Path file = Files.writeString(dir.resolve("old.txt"), "keep me", StandardCharsets.UTF_8);
        Project project = project();
        project.addFile(file);
        assertThrows(FileAlreadyExistsException.class, () -> project.createFile(file));
        assertEquals(1, project.getDerivedFileList().size());
        TextDocument document = project.getDerivedFileList().get(0);
        assertEquals("keep me", document.getCurrentText());
    }

    @Test
    void aFileWithoutExtensionHasNoGrammar() throws Exception {
        Project project = project();
        Path created = project.createFile(dir.resolve("notes")).file();
        assertTrue(project.getGrammarsForFile(created).isEmpty());
    }
}
