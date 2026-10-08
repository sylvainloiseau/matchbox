package fr.cnrs.lacito.matchbox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyMigrationTest {

    // absolute on every OS (on Windows "/x" is not absolute)
    private static final Path ROOT = Path.of("/").toAbsolutePath();

    @TempDir
    Path dir;

    private Path home;
    private Path legacyDir;
    private AppDirectories dirs;

    @BeforeEach
    void setUp() throws Exception {
        home = dir.resolve("home");
        legacyDir = Files.createDirectories(home.resolve(".config/matchbox"));
        dirs = new AppDirectories(dir.resolve("new/config"), dir.resolve("new/state"), dir.resolve("new/logs"));
        // files in the formats written by the old ApplicationSettings
        Files.writeString(legacyDir.resolve("configuration.txt"), String.join("\n",
            "charset=ISO-8859-1",
            "igt_paragraph_color=#AABBCC",
            "lexical_description_color=#112233",
            "morpheme_separators==",
            "file_extension=txt,MD, .xml",
            "file_patterns=notes-*",
            "max_recent_files=4",
            "open_last_project=true",
            "unknown_key=whatever"));
        Files.writeString(legacyDir.resolve("matchbox.history"), ROOT.resolve("data/a.txt") + "\n" + ROOT.resolve("data/b.txt") + "\n\n");
        Files.writeString(legacyDir.resolve("matchbox.project.history"), ROOT.resolve("p/one.mbx") + "\n");
        Files.writeString(legacyDir.resolve("matchbox.fileset.history"), ROOT.resolve("sets/x") + "\n");
        Files.writeString(home.resolve(".matchbox.log"), "<log/>");
    }

    @Test
    void importsValuesWritesNewFilesAndDeletesOldOnes() {
        try (UserSettings s = UserSettings.load(dirs, home, 10)) {
            assertEquals(StandardCharsets.ISO_8859_1, s.charsetProperty().get());
            assertEquals("#AABBCC", s.igtColorProperty().get());
            assertEquals("#112233", s.lexicalColorProperty().get());
            assertEquals("=", s.morphemeSeparatorsProperty().get());
            assertEquals(List.of("txt", "md", "xml"), s.fileExtensions());
            assertEquals(List.of("notes-*"), s.filePatterns());
            assertEquals(4, s.maxRecentFilesProperty().get());
            assertTrue(s.openLastProjectProperty().get());
            assertEquals(List.of(ROOT.resolve("data/a.txt"), ROOT.resolve("data/b.txt")), s.recentFiles().items());
            assertEquals(List.of(ROOT.resolve("p/one.mbx")), s.recentProjects().items());
            assertTrue(s.getLoadWarnings().isEmpty(), () -> s.getLoadWarnings().toString());
        }
        assertTrue(Files.exists(dirs.configDir().resolve("settings.json")));
        assertTrue(Files.exists(dirs.stateDir().resolve("recent.json")));
        for (String f : LegacySettingsImporter.FILES) {
            assertFalse(Files.exists(legacyDir.resolve(f)), f + " deleted");
        }
        assertFalse(Files.exists(home.resolve(".matchbox.log")), "old log deleted");
        assertFalse(Files.exists(legacyDir), "empty legacy directory deleted");
        assertTrue(Files.isDirectory(home.resolve(".config")), "parent directory kept");
    }

    @Test
    void otherFilesOfTheLegacyDirectoryAreKept() throws Exception {
        Files.writeString(legacyDir.resolve("notes-of-the-user.txt"), "keep me");
        UserSettings.load(dirs, home, 10).close();
        assertTrue(Files.exists(legacyDir.resolve("notes-of-the-user.txt")));
        assertFalse(Files.exists(legacyDir.resolve("configuration.txt")));
    }

    @Test
    void onLinuxTheLegacyDirectoryIsTheNewConfigDirectoryAndIsKept() {
        AppDirectories linux = new AppDirectories(legacyDir, dir.resolve("state"), dir.resolve("state/logs"));
        UserSettings.load(linux, home, 10).close();
        assertTrue(Files.exists(legacyDir.resolve("settings.json")));
        assertFalse(Files.exists(legacyDir.resolve("configuration.txt")));
        assertFalse(Files.exists(legacyDir.resolve("matchbox.history")));
    }

    @Test
    void notImportedAgainWhenNewFilesExistButOldFilesAreDeleted() throws Exception {
        UserSettings.load(dirs, home, 10).close();
        // an older version recreates its files with other values
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("configuration.txt"), "igt_paragraph_color=#000000\n");
        try (UserSettings s = UserSettings.load(dirs, home, 10)) {
            assertEquals("#AABBCC", s.igtColorProperty().get());
        }
        assertFalse(Files.exists(legacyDir.resolve("configuration.txt")));
    }

    @Test
    void oldFilesKeptIfTheNewFilesCannotBeWritten() throws Exception {
        // a regular file where the config directory should be: writing fails
        Files.createDirectories(dir.resolve("new"));
        Files.writeString(dir.resolve("new/config"), "not a directory");
        try (UserSettings s = UserSettings.load(dirs, home, 10)) {
            assertEquals("#AABBCC", s.igtColorProperty().get(), "imported values used for this session");
            assertEquals(1, s.getLoadWarnings().size());
        }
        assertTrue(Files.exists(legacyDir.resolve("configuration.txt")));
        assertTrue(Files.exists(home.resolve(".matchbox.log")));
    }

    @Test
    void customLogFileEqualToTheOldLogIsKept() throws Exception {
        Files.writeString(legacyDir.resolve("configuration.txt"), "logfile=" + home.resolve(".matchbox.log") + "\n");
        try (UserSettings s = UserSettings.load(dirs, home, 10)) {
            assertEquals(home.resolve(".matchbox.log"), s.customLogFile().orElseThrow());
        }
        assertTrue(Files.exists(home.resolve(".matchbox.log")));
    }

    @Test
    void relativeLegacyLogFileIsResolvedAgainstTheLegacyDirectory() throws Exception {
        Files.writeString(legacyDir.resolve("configuration.txt"), "logfile=logs/m.log\n");
        try (UserSettings s = UserSettings.load(dirs, home, 10)) {
            assertEquals(legacyDir.resolve("logs/m.log"), s.customLogFile().orElseThrow());
        }
    }
}
