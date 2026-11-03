package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectorySettingsTest {

    @TempDir
    Path dir;
    private Path notes;

    @BeforeEach
    void setUp() throws Exception {
        notes = Files.createDirectories(dir.resolve("notes"));
        Files.createDirectories(notes.resolve("2024"));
        Files.createDirectories(notes.resolve("drafts/old"));
        Files.writeString(notes.resolve("a.txt"), "a");
        Files.writeString(notes.resolve("B.TXT"), "b");
        Files.writeString(notes.resolve("c.md"), "c");
        Files.writeString(notes.resolve("image.png"), "x");
        Files.writeString(notes.resolve("2024/day1.txt"), "d");
        Files.writeString(notes.resolve("drafts/old/x.txt"), "x");
    }

    private List<String> scan(DirectorySettings s) throws Exception {
        return s.scan(notes).stream().map(f -> DirectorySettings.relativePath(notes, f)).toList();
    }

    @Test
    void globsWithoutSlashMatchTheNameAtAnyDepthIgnoringCase() {
        DirectorySettings s = new DirectorySettings(List.of("*.txt"), List.of(), true);
        assertTrue(s.accepts("a.txt"));
        assertTrue(s.accepts("2024/day1.txt"));
        assertTrue(s.accepts("B.TXT"), "case is ignored");
        assertFalse(s.accepts("c.md"));
    }

    @Test
    void globsWithSlashMatchTheRelativePath() {
        assertTrue(Glob.of("drafts/**").matches("drafts/old/x.txt"));
        assertFalse(Glob.of("drafts/**").matches("other/drafts/x.txt"));
        assertTrue(Glob.of("drafts/").matches("drafts/x.txt"), "a trailing / means everything under it");
        assertTrue(Glob.of("/a.txt").matches("a.txt"));
        assertFalse(Glob.of("/a.txt").matches("2024/a.txt"), "a leading / anchors");
        assertTrue(Glob.of("**/*.txt").matches("a.txt"));
        assertTrue(Glob.of("2024/*.{txt,md}").matches("2024/day1.md"));
        assertFalse(Glob.of("2024/*.txt").matches("2024/sub/day1.txt"), "* does not cross /");
        assertTrue(Glob.of("day[0-9].txt").matches("2024/day1.txt"));
        assertTrue(Glob.of("day[!0-9].txt").matches("dayx.txt"));
        assertTrue(Glob.of("a+b(1).txt").matches("a+b(1).txt"), "regex characters are literal");
    }

    @Test
    void invalidPatternsAreReported() {
        assertNotNull(Glob.problem("[a-"));
        assertNotNull(Glob.problem("{a,b"));
        assertNull(Glob.problem("*.txt"));
        assertEquals(1, new DirectorySettings(List.of("*.txt", "[x"), List.of(), true).problems().size());
    }

    @Test
    void scanAppliesIncludesExcludesAndRecursion() throws Exception {
        assertEquals(List.of("2024/day1.txt", "a.txt", "B.TXT", "drafts/old/x.txt"),
            scan(new DirectorySettings(List.of("*.txt"), List.of(), true)));
        assertEquals(List.of("2024/day1.txt", "a.txt", "B.TXT"),
            scan(new DirectorySettings(List.of("*.txt"), List.of("drafts/**"), true)), "exclude wins");
        assertEquals(List.of("a.txt", "B.TXT"), scan(new DirectorySettings(List.of("*.txt"), List.of(), false)));
        assertEquals(List.of("a.txt", "B.TXT", "c.md", "image.png"),
            scan(new DirectorySettings(List.of(), List.of(), false)), "no include: every file");
        assertEquals(List.of("*.txt", "*.md"), DirectorySettings.of(List.of("txt", "md"), true).includes());
    }

    private Project project() {
        Project p = Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
        p.setApplyExecutor(Runnable::run);
        // no file type: loading does not need the grammars
        List.copyOf(p.getFileTypes()).forEach(p::removeFileType);
        return p;
    }

    private static boolean has(Project p, Path file) {
        return p.getDerivedFileList().stream().anyMatch(d -> d.getFilePath().equals(file));
    }

    @Test
    void newSettingsAddAndRemoveFiles() throws Exception {
        Project p = project();
        p.addDirectory(notes, new DirectorySettings(List.of("*.txt"), List.of(), false));
        assertEquals(List.of(notes.resolve("a.txt"), notes.resolve("B.TXT")), p.filesOf(notes));
        assertEquals(notes, p.directoryOf(notes.resolve("a.txt")).orElseThrow());

        DirectorySettings next = new DirectorySettings(List.of("*.txt", "*.md"), List.of("B.TXT"), true);
        List<TextDocument> removed = p.documentsRemovedBy(notes, next);
        assertEquals(List.of(notes.resolve("B.TXT")), removed.stream().map(TextDocument::getFilePath).toList());
        Project.DirectoryChange change = p.setDirectorySettings(notes, next);
        assertEquals(List.of(notes.resolve("B.TXT")), change.removed());
        assertEquals(List.of(notes.resolve("2024/day1.txt"), notes.resolve("c.md"), notes.resolve("drafts/old/x.txt")),
            change.added());
        assertFalse(has(p, notes.resolve("B.TXT")));
        assertTrue(has(p, notes.resolve("c.md")));
        assertEquals(next, p.directorySettings(notes).orElseThrow());
    }

    @Test
    void rescanFindsNewFilesAndAFileAddedAloneIsKept() throws Exception {
        Project p = project();
        p.addFile(notes.resolve("a.txt"));
        p.addDirectory(notes, new DirectorySettings(List.of("*.txt"), List.of(), false));
        assertTrue(p.directoryOf(notes.resolve("a.txt")).isEmpty(), "added alone before");
        Files.writeString(notes.resolve("new.txt"), "n");
        assertEquals(List.of(notes.resolve("new.txt")), p.rescanDirectory(notes).added());
        int revision = p.definitionRevisionProperty().get();
        p.removeDirectory(notes);
        assertTrue(p.definitionRevisionProperty().get() > revision);
        assertTrue(has(p, notes.resolve("a.txt")), "the file added alone stays");
        assertFalse(has(p, notes.resolve("new.txt")));
    }

    @Test
    void settingsAndOrderAreSavedAndARecursiveDirectoryStaysRecursive() throws Exception {
        Project p = project();
        Path alone = Files.writeString(dir.resolve("alone.txt"), "x");
        p.addDirectory(notes, new DirectorySettings(List.of("*.txt"), List.of("drafts/**"), true));
        p.addFile(alone);
        Path mbx = dir.resolve("p.mbx");
        p.setFile(mbx.toFile());
        ProjectXMLSerializer.save(p);

        Project loaded = ProjectXMLSerializer.load(mbx.toFile(), List.of());
        assertEquals(List.of(notes, alone), loaded.getDefinitionPaths(), "definition order kept");
        assertEquals(p.directorySettings(notes), loaded.directorySettings(notes));
        assertTrue(loaded.filesOf(notes).contains(notes.resolve("2024/day1.txt")), "still recursive after reopening");
        assertFalse(loaded.definitionChangedProperty().get());
    }

    @Test
    void aDirectoryOfAnOlderProjectFileLoadsAsBefore() throws Exception {
        Path mbx = dir.resolve("old.mbx");
        Files.writeString(mbx, "<project><fileset><directory path=\"" + notes + "\"/></fileset></project>");
        Project loaded = ProjectXMLSerializer.load(mbx.toFile(), List.of());
        assertEquals(DirectorySettings.of(List.of("txt", "md"), false), loaded.directorySettings(notes).orElseThrow());
        assertEquals(List.of(notes.resolve("a.txt"), notes.resolve("B.TXT"), notes.resolve("c.md")), loaded.filesOf(notes));
    }
}
