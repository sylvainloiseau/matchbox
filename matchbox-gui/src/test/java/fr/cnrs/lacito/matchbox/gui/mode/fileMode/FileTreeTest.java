package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.gui.DirectorySettingsDialog;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree.Entry;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree.FileNode;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree.SubDirectory;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree.TopDirectory;
import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTreeTest {

    @TempDir
    Path dir;
    private Path notes;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        notes = Files.createDirectories(dir.resolve("notes"));
        Files.createDirectories(notes.resolve("2024/jan"));
        Files.createDirectories(notes.resolve("empty"));
        Files.writeString(notes.resolve("b.txt"), "b");
        Files.writeString(notes.resolve("A.txt"), "a");
        Files.writeString(notes.resolve("2024/day1.txt"), "d");
        Files.writeString(notes.resolve("2024/jan/day2.txt"), "d");
        Files.writeString(notes.resolve("empty/image.png"), "x");
        project = Project.createDefaultProject(PluginManager.loadPlugins());
        project.setApplyExecutor(Runnable::run);
    }

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(e -> e.node().name()).toList();
    }

    @Test
    void filesAddedAloneAreNeverGrouped() throws Exception {
        Path loose = Files.createDirectories(dir.resolve("loose"));
        Path x = Files.writeString(loose.resolve("x.txt"), "x");
        Path y = Files.writeString(loose.resolve("y.txt"), "y");
        project.addFile(y);
        project.addFile(x);
        List<Entry> tree = FileTree.build(project, "");
        assertEquals(List.of("y.txt", "x.txt"), names(tree), "definition order");
        assertEquals(new FileNode(project.getParsedText(y), Optional.empty()), tree.get(0).node());
    }

    @Test
    void aDirectoryGroupsItsFilesWithSubDirectoriesFirst() throws Exception {
        project.addDirectory(notes, new DirectorySettings(List.of("*.txt"), List.of(), true));
        List<Entry> tree = FileTree.build(project, null);
        assertEquals(1, tree.size());
        Entry top = tree.get(0);
        assertEquals(new TopDirectory(notes), top.node());
        assertEquals(List.of("2024", "A.txt", "b.txt"), names(top.children()), "empty (no .txt) sub-directory hidden");
        Entry y2024 = top.children().get(0);
        assertEquals(new SubDirectory(notes, notes.resolve("2024")), y2024.node());
        assertEquals(List.of("jan", "day1.txt"), names(y2024.children()));
        assertEquals(4, top.fileCount());
        assertEquals(Optional.of(notes), ((FileNode) y2024.children().get(1).node()).directory());
    }

    @Test
    void theFilterKeepsTheDirectoriesOfTheMatchingFiles() throws Exception {
        project.addDirectory(notes, new DirectorySettings(List.of("*.txt"), List.of(), true));
        Path alone = Files.writeString(dir.resolve("other.txt"), "o");
        project.addFile(alone);
        List<Entry> tree = FileTree.build(project, "DAY2");
        assertEquals(List.of(notes.toString()), names(tree));
        assertEquals(List.of("2024"), names(tree.get(0).children()));
        assertEquals(List.of("jan"), names(tree.get(0).children().get(0).children()));
        assertTrue(FileTree.build(project, "nothing matches").isEmpty());
    }

    @Test
    void theDialogPreviewSaysWhatChanges() {
        Path a = notes.resolve("A.txt");
        Path b = notes.resolve("b.txt");
        Path d = notes.resolve("2024/day1.txt");
        DirectorySettingsDialog.Preview p = DirectorySettingsDialog.Preview.of(notes, List.of(a, b), List.of(a, d));
        assertEquals("2 files (1 added, 1 removed)", p.summary());
        assertEquals(List.of("+ 2024/day1.txt", "− b.txt", "   A.txt"), p.lines());
        assertEquals("1 file", DirectorySettingsDialog.Preview.of(notes, List.of(a), List.of(a)).summary());
    }
}
