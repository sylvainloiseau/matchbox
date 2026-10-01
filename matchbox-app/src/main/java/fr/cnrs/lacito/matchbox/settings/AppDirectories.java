package fr.cnrs.lacito.matchbox.settings;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Per-user directories of the application, following the conventions of each OS.
 *
 * <table>
 *   <caption>Locations</caption>
 *   <tr><th>OS</th><th>config</th><th>state</th><th>logs</th></tr>
 *   <tr><td>macOS</td><td>~/Library/Application Support/Matchbox</td><td>same</td><td>~/Library/Logs/Matchbox</td></tr>
 *   <tr><td>Windows</td><td>%APPDATA%\Matchbox</td><td>%LOCALAPPDATA%\Matchbox</td><td>%LOCALAPPDATA%\Matchbox\Logs</td></tr>
 *   <tr><td>Linux, Unix</td><td>$XDG_CONFIG_HOME/matchbox</td><td>$XDG_STATE_HOME/matchbox</td><td>$XDG_STATE_HOME/matchbox/logs</td></tr>
 * </table>
 *
 * <p>The system property {@value #OVERRIDE_PROPERTY} or the environment variable
 * {@value #OVERRIDE_ENV} puts everything in one directory (tests, portable installs). The
 * directories are not created here: they are created when a file is first written.
 *
 * @param configDir preferences ({@code settings.json})
 * @param stateDir  state such as recent files ({@code recent.json})
 * @param logDir    log files
 */
public record AppDirectories(Path configDir, Path stateDir, Path logDir) {

    public static final String OVERRIDE_PROPERTY = "matchbox.settings.dir";
    public static final String OVERRIDE_ENV = "MATCHBOX_SETTINGS_DIR";

    static final String APP_NAME = "Matchbox";
    static final String UNIX_APP_NAME = "matchbox";

    public AppDirectories {
        Objects.requireNonNull(configDir, "configDir");
        Objects.requireNonNull(stateDir, "stateDir");
        Objects.requireNonNull(logDir, "logDir");
    }

    /** Directories for the current user and OS. */
    public static AppDirectories forCurrentUser() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (isBlank(override)) {
            override = System.getenv(OVERRIDE_ENV);
        }
        return resolve(
            System.getProperty("os.name", ""),
            System.getenv(),
            Path.of(System.getProperty("user.home")),
            override
        );
    }

    /** Resolution rules, with the OS, environment and home directory given explicitly (for tests). */
    static AppDirectories resolve(String osName, Map<String, String> env, Path home, String override) {
        if (!isBlank(override)) {
            Path dir = Path.of(override.strip()).toAbsolutePath();
            return new AppDirectories(dir, dir, dir.resolve("logs"));
        }
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            Path library = home.resolve("Library");
            Path support = library.resolve("Application Support").resolve(APP_NAME);
            return new AppDirectories(support, support, library.resolve("Logs").resolve(APP_NAME));
        }
        if (os.contains("win")) {
            // the values come from the environment as they are: they are only checked to be set,
            // because a Windows path is not "absolute" for the Path implementation of another OS
            Path roaming = envPath(env, "APPDATA", home.resolve("AppData").resolve("Roaming"), false);
            Path local = envPath(env, "LOCALAPPDATA", home.resolve("AppData").resolve("Local"), false);
            Path state = local.resolve(APP_NAME);
            return new AppDirectories(roaming.resolve(APP_NAME), state, state.resolve("Logs"));
        }
        // XDG Base Directory Specification: relative values must be ignored
        Path config = envPath(env, "XDG_CONFIG_HOME", home.resolve(".config"), true);
        Path state = envPath(env, "XDG_STATE_HOME", home.resolve(".local").resolve("state"), true)
            .resolve(UNIX_APP_NAME);
        return new AppDirectories(config.resolve(UNIX_APP_NAME), state, state.resolve("logs"));
    }

    private static Path envPath(Map<String, String> env, String name, Path fallback, boolean requireAbsolute) {
        String value = env.get(name);
        if (isBlank(value)) {
            return fallback;
        }
        Path path = Path.of(value.strip());
        return requireAbsolute && !path.isAbsolute() ? fallback : path;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
