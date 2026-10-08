package fr.cnrs.lacito.matchbox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppDirectoriesTest {

    // absolute on every OS ("/" on Unix, the current drive on Windows, where "/x" is not absolute)
    private static final Path ROOT = Path.of("/").toAbsolutePath();
    private static final Path HOME = ROOT.resolve("home/u");

    @Test
    void macOs() {
        AppDirectories d = AppDirectories.resolve("Mac OS X", Map.of(), HOME, null);
        assertEquals(HOME.resolve("Library/Application Support/Matchbox"), d.configDir());
        assertEquals(d.configDir(), d.stateDir());
        assertEquals(HOME.resolve("Library/Logs/Matchbox"), d.logDir());
    }

    @Test
    void windowsFromEnvironment() {
        AppDirectories d = AppDirectories.resolve("Windows 11",
            Map.of("APPDATA", "/win/Roaming", "LOCALAPPDATA", "/win/Local"), HOME, null);
        assertEquals(Path.of("/win/Roaming/Matchbox"), d.configDir());
        assertEquals(Path.of("/win/Local/Matchbox"), d.stateDir());
        assertEquals(Path.of("/win/Local/Matchbox/Logs"), d.logDir());
    }

    @Test
    void windowsWithoutEnvironment() {
        AppDirectories d = AppDirectories.resolve("Windows 10", Map.of(), HOME, null);
        assertEquals(HOME.resolve("AppData/Roaming/Matchbox"), d.configDir());
        assertEquals(HOME.resolve("AppData/Local/Matchbox"), d.stateDir());
    }

    @Test
    void linuxDefaults() {
        AppDirectories d = AppDirectories.resolve("Linux", Map.of(), HOME, null);
        assertEquals(HOME.resolve(".config/matchbox"), d.configDir());
        assertEquals(HOME.resolve(".local/state/matchbox"), d.stateDir());
        assertEquals(HOME.resolve(".local/state/matchbox/logs"), d.logDir());
    }

    @Test
    void linuxXdgVariables() {
        AppDirectories d = AppDirectories.resolve("Linux",
            Map.of("XDG_CONFIG_HOME", ROOT.resolve("xdg/config").toString(),
                "XDG_STATE_HOME", ROOT.resolve("xdg/state").toString()), HOME, null);
        assertEquals(ROOT.resolve("xdg/config/matchbox"), d.configDir());
        assertEquals(ROOT.resolve("xdg/state/matchbox"), d.stateDir());
    }

    @Test
    void relativeXdgValuesAreIgnored() {
        AppDirectories d = AppDirectories.resolve("FreeBSD",
            Map.of("XDG_CONFIG_HOME", "relative/config", "XDG_STATE_HOME", " "), HOME, null);
        assertEquals(HOME.resolve(".config/matchbox"), d.configDir());
        assertEquals(HOME.resolve(".local/state/matchbox"), d.stateDir());
    }

    @Test
    void overridePutsEverythingInOneDirectory() {
        AppDirectories d = AppDirectories.resolve("Mac OS X", Map.of(), HOME, ROOT.resolve("portable/settings").toString());
        assertEquals(ROOT.resolve("portable/settings"), d.configDir());
        assertEquals(ROOT.resolve("portable/settings"), d.stateDir());
        assertEquals(ROOT.resolve("portable/settings/logs"), d.logDir());
    }
}
