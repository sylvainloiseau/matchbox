package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The order of a project's files: explicit expected orders, the same on every system. */
class ProjectFileOrderTest {

    @TempDir
    Path dir;

    private static List<String> names(Path root, List<Path> paths) {
        return paths.stream().map(p -> root.relativize(p).toString().replace('\\', '/')).toList();
    }

    @Test
    void byPathFilesAndFoldersOfAFolderAreSortedTogetherByName() {
        Path r = dir;
        List<Path> files = List.of(r.resolve("a/y.txt"), r.resolve("B.TXT"), r.resolve("a/b/x.txt"),
            r.resolve("a.txt"), r.resolve("a/b.txt"));
        List<Path> ordered = ProjectFileOrder.PATH.order(files, p -> Optional.empty());
        // case ignored first (a.txt < B.TXT on every system); "b" < "b.txt" < "y.txt" in a/
        assertEquals(List.of("a/b/x.txt", "a/b.txt", "a/y.txt", "a.txt", "B.TXT"), names(r, ordered));
    }

    @Test
    void byPathNamesInNfdAndNfcAreTheSame() {
        Path nfd = dir.resolve("éte.txt");      // é as e + combining accent (macOS)
        Path nfc = dir.resolve("f.txt");
        Path before = dir.resolve("e.txt");
        // é as e + combining accent (macOS) or as one character (Linux, Windows): the same place,
        // after f (FileOrder compares code points after NFC, not a language's collation)
        for (String ete : List.of("éte.txt", "éte.txt")) {
            Path accented = dir.resolve(ete);
            assertEquals(List.of(before, nfc, accented),
                ProjectFileOrder.PATH.order(List.of(accented, nfc, before), p -> Optional.empty()), ete);
        }
        assertEquals(ProjectFileOrder.PATH.order(List.of(nfd, nfc, before), p -> Optional.empty()).indexOf(nfd), 2);
    }

    @Test
    void byDateThenByPathAndUndatedFilesLast() {
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Path c = dir.resolve("c.txt");
        Path d = dir.resolve("d.txt");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-02-01T00:00:00Z");
        Map<Path, Instant> dates = Map.of(a, t2, b, t1, c, t1);
        List<Path> ordered = ProjectFileOrder.MODIFIED.order(List.of(d, c, b, a), p -> Optional.ofNullable(dates.get(p)));
        assertEquals(List.of(b, c, a, d), ordered, "b and c have the same date: by path; d has none: last");
    }

    @Test
    void headerDates() {
        assertEquals(Optional.of(Instant.parse("2025-07-14T00:00:00Z")), ProjectFileOrder.parseHeaderDate("2025-07-14"));
        assertEquals(Optional.of(Instant.parse("2025-07-14T00:00:00Z")), ProjectFileOrder.parseHeaderDate("\"2025-07-14\""));
        assertEquals(Optional.of(Instant.parse("2025-07-14T10:30:00Z")), ProjectFileOrder.parseHeaderDate("2025-07-14T10:30"));
        assertEquals(Optional.of(Instant.parse("2025-07-14T08:30:00Z")),
            ProjectFileOrder.parseHeaderDate("2025-07-14T10:30+02:00"));
        assertTrue(ProjectFileOrder.parseHeaderDate("14/07/2025").isEmpty());
        assertTrue(ProjectFileOrder.parseHeaderDate(null).isEmpty());
    }

    @Test
    void xmlNames() {
        assertEquals(Optional.of(ProjectFileOrder.PATH), ProjectFileOrder.fromXml(null));
        assertEquals(Optional.of(ProjectFileOrder.MODIFIED), ProjectFileOrder.fromXml("modified"));
        assertEquals(Optional.of(ProjectFileOrder.HEADER_DATE), ProjectFileOrder.fromXml("Header-Date"));
        assertFalse(ProjectFileOrder.fromXml("alphabetical").isPresent());
    }

    @Test
    void theOrderIsSavedAndLoadedWithTheProject() throws Exception {
        Project p = Project.createDefaultProject(List.of());
        List.copyOf(p.getFileTypes()).forEach(p::removeFileType);
        Path file = dir.resolve("p.mbx");
        p.setFile(file.toFile());
        ProjectXMLSerializer.save(p);
        assertFalse(Files.readString(file).contains("fileOrder"), "by path: nothing written");
        p.setFileOrder(ProjectFileOrder.HEADER_DATE);
        assertTrue(p.definitionChangedProperty().get());
        ProjectXMLSerializer.save(p);
        assertTrue(Files.readString(file).contains("<fileOrder by=\"header-date\"/>"));
        Project loaded = ProjectXMLSerializer.load(file.toFile(), List.of());
        assertEquals(ProjectFileOrder.HEADER_DATE, loaded.getFileOrder());
        assertTrue(loaded.getLoadProblems().isEmpty());
    }

    @Test
    void anUnknownOrderIsReportedAndPathIsUsed() throws Exception {
        Path file = dir.resolve("p.mbx");
        Files.writeString(file, "<project><fileOrder by=\"alphabetical\"/></project>");
        Project loaded = ProjectXMLSerializer.load(file.toFile(), List.of());
        assertEquals(ProjectFileOrder.PATH, loaded.getFileOrder());
        assertEquals(1, loaded.getLoadProblems().size());
    }

    @Test
    void anEditAppliesTheOrder() {
        Project p = Project.createDefaultProject(List.of());
        ProjectEdit edit = ProjectEdit.of(p);
        assertEquals(ProjectFileOrder.PATH, edit.getFileOrder());
        edit.setFileOrder(ProjectFileOrder.MODIFIED);
        edit.applyTo(p);
        assertEquals(ProjectFileOrder.MODIFIED, p.getFileOrder());
    }
}
