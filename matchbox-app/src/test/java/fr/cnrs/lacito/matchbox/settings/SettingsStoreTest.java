package fr.cnrs.lacito.matchbox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsStoreTest {

    @TempDir
    Path dir;

    private SettingsStore store;

    @BeforeEach
    void setUp() {
        store = new SettingsStore(new AppDirectories(dir.resolve("config"), dir.resolve("state"), dir.resolve("logs")));
    }

    @Test
    void missingFilesGiveDefaults() {
        var loaded = store.loadSettings();
        assertEquals(SettingsData.defaults(), loaded.value());
        assertTrue(loaded.problems().isEmpty());
        assertEquals(RecentData.empty(), store.loadRecent().value());
    }

    @Test
    void roundTrip() throws IOException {
        SettingsData data = new SettingsData(1, "ISO-8859-1", "#123456", "red", "-",
            List.of("txt"), List.of("notes-*"), 5, 7, true, "/tmp/m.log");
        store.saveSettings(data);
        store.saveRecent(new RecentData(1, List.of("/a.txt", "/b"), List.of("/p.mbx")));

        assertEquals(data, store.loadSettings().value());
        assertEquals(List.of("/a.txt", "/b"), store.loadRecent().value().recentFiles());
        assertEquals(List.of("/p.mbx"), store.loadRecent().value().recentProjects());
        assertTrue(Files.isRegularFile(dir.resolve("config/settings.json")));
        assertTrue(Files.isRegularFile(dir.resolve("state/recent.json")));
    }

    @Test
    void saveLeavesNoTemporaryFile() throws IOException {
        store.saveSettings(SettingsData.defaults());
        store.saveSettings(SettingsData.defaults());
        try (var files = Files.list(dir.resolve("config"))) {
            assertEquals(List.of("settings.json"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void nullLogFileIsNotWritten() throws IOException {
        store.saveSettings(SettingsData.defaults());
        assertFalse(Files.readString(store.settingsFile()).contains("logFile"));
    }

    @Test
    void unknownKeysAreIgnoredAndMissingKeysGetDefaults() throws IOException {
        Files.createDirectories(store.settingsFile().getParent());
        Files.writeString(store.settingsFile(), "{ \"igtColor\": \"#abcdef\", \"futureOption\": [1, 2] }");
        var loaded = store.loadSettings();
        assertEquals("#abcdef", loaded.value().igtColor());
        assertEquals(SettingsData.defaults().lexicalColor(), loaded.value().lexicalColor());
        assertEquals(List.of("txt", "md"), loaded.value().fileExtensions());
        assertTrue(loaded.problems().isEmpty());
    }

    @Test
    void invalidValuesAreReplacedAndReported() throws IOException {
        Files.createDirectories(store.settingsFile().getParent());
        Files.writeString(store.settingsFile(),
            "{ \"charset\": \"NO-SUCH-CHARSET\", \"igtColor\": \"not a colour!\", \"maxRecentFiles\": 0," +
            "  \"fileExtensions\": [\".TXT\", \" md \", \"txt\", \"\"] }");
        var loaded = store.loadSettings();
        assertEquals("UTF-8", loaded.value().charset());
        assertEquals("#fff1c1", loaded.value().igtColor());
        assertEquals(10, loaded.value().maxRecentFiles());
        assertEquals(List.of("txt", "md"), loaded.value().fileExtensions());
        assertEquals(3, loaded.problems().size());
    }

    @Test
    void brokenFileIsRenamedAndDefaultsUsed() throws IOException {
        Files.createDirectories(store.settingsFile().getParent());
        Files.writeString(store.settingsFile(), "{ this is not json");
        var loaded = store.loadSettings();
        assertEquals(SettingsData.defaults(), loaded.value());
        assertEquals(1, loaded.problems().size());
        assertFalse(Files.exists(store.settingsFile()));
        try (var files = Files.list(dir.resolve("config"))) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("settings.json.broken-")));
        }
    }

    @Test
    void blankLogFileBecomesNull() {
        SettingsData d = new SettingsData(null, null, null, null, null, null, null, null, null, null, "  ").validated();
        assertNull(d.logFile());
        assertEquals(SettingsData.defaults(), d);
    }
}
