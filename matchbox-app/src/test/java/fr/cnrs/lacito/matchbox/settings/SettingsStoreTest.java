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
        AssistSettingsData assist = new AssistSettingsData("ollama", "http://localhost:11434", "llama3.2",
            "qwen3:8b", false, AssistSettingsData.AUTO, 600, 3000, 500, true, false, "French", List.of("anthropic"));
        SettingsData data = new SettingsData(SettingsData.CURRENT_VERSION, "ISO-8859-1", "-",
            List.of("txt"), List.of("notes-*"), 5, 7, true, "/tmp/m.log", assist, true,
            new TagBrowserSettingsData(2, 8, TagBrowserSettingsData.SORT_COUNT, true),
            new EditorSettingsData(true, false, 16, "Menlo", false, true, false, List.of("ʔ", "ŋ"), false));
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
        Files.writeString(store.settingsFile(), "{ \"morphemeSeparators\": \"=\", \"igtColor\": \"#abcdef\", \"futureOption\": [1, 2] }");
        var loaded = store.loadSettings();
        assertEquals("=", loaded.value().morphemeSeparators());
        assertEquals(SettingsData.defaults().charset(), loaded.value().charset());
        assertEquals(List.of("txt", "md"), loaded.value().fileExtensions());
        assertTrue(loaded.problems().isEmpty());
    }

    @Test
    void invalidValuesAreReplacedAndReported() throws IOException {
        Files.createDirectories(store.settingsFile().getParent());
        Files.writeString(store.settingsFile(),
            "{ \"charset\": \"NO-SUCH-CHARSET\", \"maxRecentProjects\": -1, \"maxRecentFiles\": 0," +
            "  \"fileExtensions\": [\".TXT\", \" md \", \"txt\", \"\"] }");
        var loaded = store.loadSettings();
        assertEquals("UTF-8", loaded.value().charset());
        assertEquals(10, loaded.value().maxRecentProjects());
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
        SettingsData d = new SettingsData(null, null, null, null, null, null, null, null, "  ", null, null, null, null).validated();
        assertNull(d.logFile());
        assertEquals(SettingsData.defaults(), d);
    }

    @Test
    void version1FilesGetDefaultAssistantSettings() throws Exception {
        java.nio.file.Files.createDirectories(store.settingsFile().getParent());
        java.nio.file.Files.writeString(store.settingsFile(),
            "{\"version\":1,\"charset\":\"UTF-8\",\"maxRecentFiles\":4}");
        SettingsData d = store.loadSettings().value();
        assertEquals(SettingsData.CURRENT_VERSION, d.version());
        assertEquals(4, d.maxRecentFiles());
        assertEquals(AssistSettingsData.defaults(), d.assist());
    }

    @Test
    void invalidAssistantValuesAreReplaced() {
        List<String> problems = new java.util.ArrayList<>();
        AssistSettingsData a = new AssistSettingsData("gemini", null, null, null, null, "sometimes", 5, null, null,
            null, null, " ", java.util.Arrays.asList("openai", "openai", "x", null)).validated(problems::add);
        assertEquals("none", a.provider());
        assertEquals(AssistSettingsData.MANUAL, a.trigger());
        assertEquals(AssistSettingsData.defaults().debounceMs(), a.debounceMs());
        assertEquals("English", a.language());
        assertEquals(List.of("openai"), a.cloudConsentGiven());
        assertEquals(3, problems.size());
    }
}
