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

class UserSettingsTest {

    @TempDir
    Path dir;

    private AppDirectories dirs;
    private Path home;

    @BeforeEach
    void setUp() {
        home = dir.resolve("home");
        dirs = new AppDirectories(dir.resolve("config"), dir.resolve("state"), dir.resolve("logs"));
    }

    private UserSettings load(long delay) {
        return UserSettings.load(dirs, home, delay);
    }

    @Test
    void defaultsWithoutAnyFileAndNothingWritten() {
        try (UserSettings s = load(10)) {
            assertEquals(StandardCharsets.UTF_8, s.charsetProperty().get());
            assertEquals(List.of("txt", "md"), s.fileExtensions());
            assertTrue(s.getLoadReport().isEmpty());
        }
        assertFalse(Files.exists(dirs.configDir().resolve("settings.json")));
    }

    @Test
    void changesAreSavedAfterTheDelay() throws Exception {
        try (UserSettings s = load(50)) {
            s.igtColorProperty().set("#010203");
            s.openLastProjectProperty().set(true);
            s.recentProjects().add(Path.of("/p/one.mbx"));
            assertFalse(Files.exists(dirs.configDir().resolve("settings.json")), "not saved before the delay");
            Thread.sleep(400);
            assertTrue(Files.exists(dirs.configDir().resolve("settings.json")));
        }
        try (UserSettings reloaded = load(50)) {
            assertEquals("#010203", reloaded.igtColorProperty().get());
            assertTrue(reloaded.openLastProjectProperty().get(), "open last project is persisted");
            assertEquals(List.of(Path.of("/p/one.mbx")), reloaded.recentProjects().items());
        }
    }

    @Test
    void closeWritesPendingChanges() {
        try (UserSettings s = load(60_000)) {
            s.morphemeSeparatorsProperty().set("=");
        }
        try (UserSettings reloaded = load(10)) {
            assertEquals("=", reloaded.morphemeSeparatorsProperty().get());
        }
    }

    @Test
    void applyValidatesAndKeepsLogFile() {
        try (UserSettings s = load(60_000)) {
            SettingsData edited = s.snapshot();
            SettingsData changed = new SettingsData(edited.version(), "ISO-8859-1", "bad colour!", edited.lexicalColor(),
                edited.morphemeSeparators(), List.of(".XML", "txt"), List.of(), 0, 3, true, edited.logFile(), edited.assist());
            s.apply(changed);
            assertEquals(StandardCharsets.ISO_8859_1, s.charsetProperty().get());
            assertEquals(SettingsData.defaults().igtColor(), s.igtColorProperty().get());
            assertEquals(List.of("xml", "txt"), s.fileExtensions());
            assertEquals(10, s.maxRecentFilesProperty().get());
            assertEquals(3, s.maxRecentProjectsProperty().get());
            assertEquals(StandardCharsets.ISO_8859_1, s.documentOptions().charset());
        }
    }

    @Test
    void relativeLogFileIsResolvedAgainstConfigDir() throws Exception {
        Files.createDirectories(dirs.configDir());
        Files.writeString(dirs.configDir().resolve("settings.json"), "{ \"logFile\": \"logs/my.log\" }");
        try (UserSettings s = load(10)) {
            assertEquals(dirs.configDir().resolve("logs/my.log"), s.customLogFile().orElseThrow());
        }
    }

    @Test
    void brokenFileIsReportedAsWarning() throws Exception {
        Files.createDirectories(dirs.configDir());
        Files.writeString(dirs.configDir().resolve("settings.json"), "not json");
        try (UserSettings s = load(10)) {
            assertEquals(1, s.getLoadWarnings().size());
        }
    }
}
