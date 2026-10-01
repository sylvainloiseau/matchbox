package fr.cnrs.lacito.matchbox.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reads and writes {@code settings.json} (config directory) and {@code recent.json} (state
 * directory). Writes are atomic: the content is written to a temporary file which then replaces
 * the target, so a crash never leaves a truncated file.
 *
 * <p>An unreadable file is renamed {@code <name>.broken-<timestamp>} and the defaults are used.
 */
public final class SettingsStore {

    public static final String SETTINGS_FILE = "settings.json";
    public static final String RECENT_FILE = "recent.json";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AppDirectories directories;
    private final ObjectMapper mapper = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /** A loaded value with the problems met while loading it (to be shown to the user). */
    public record Loaded<T>(T value, List<String> problems) {}

    public SettingsStore(AppDirectories directories) {
        this.directories = directories;
    }

    public AppDirectories directories() {
        return directories;
    }

    public Path settingsFile() {
        return directories.configDir().resolve(SETTINGS_FILE);
    }

    public Path recentFile() {
        return directories.stateDir().resolve(RECENT_FILE);
    }

    public boolean hasSettingsFile() {
        return Files.exists(settingsFile());
    }

    public boolean hasRecentFile() {
        return Files.exists(recentFile());
    }

    public Loaded<SettingsData> loadSettings() {
        List<String> problems = new ArrayList<>();
        SettingsData data = read(settingsFile(), SettingsData.class, SettingsData::defaults, problems);
        return new Loaded<>(data.validated(problems::add), problems);
    }

    public Loaded<RecentData> loadRecent() {
        List<String> problems = new ArrayList<>();
        RecentData data = read(recentFile(), RecentData.class, RecentData::empty, problems);
        return new Loaded<>(data.validated(), problems);
    }

    public void saveSettings(SettingsData data) throws IOException {
        write(settingsFile(), data);
    }

    public void saveRecent(RecentData data) throws IOException {
        write(recentFile(), data);
    }

    private <T> T read(Path file, Class<T> type, Supplier<T> defaults, List<String> problems) {
        if (!Files.exists(file)) {
            return defaults.get();
        }
        try {
            T value = mapper.readValue(file.toFile(), type);
            if (value == null) {
                throw new IOException("empty file");
            }
            return value;
        } catch (IOException e) {
            Path broken = file.resolveSibling(file.getFileName() + ".broken-" + LocalDateTime.now().format(STAMP));
            String message = "Unreadable settings file " + file + " (" + e.getMessage().lines().findFirst().orElse("") +
                "); default values are used";
            try {
                Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
                message += "; the file was renamed " + broken.getFileName();
            } catch (IOException moveError) {
                message += "; it could not be renamed: " + moveError.getMessage();
            }
            problems.add(message);
            return defaults.get();
        }
    }

    private void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(tmp.toFile(), value);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
