package fr.cnrs.lacito.matchbox.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Imports the settings of the versions before 2026-10 ({@code ~/.config/matchbox/configuration.txt}
 * and the {@code *.history} files) and deletes the old files once the new ones are written.
 *
 * <p>Only the legacy file names and {@code ~/.matchbox.log} are deleted, never a directory
 * recursively. On Linux the legacy directory is also the new configuration directory.
 */
final class LegacySettingsImporter {

    static final String CONFIG = "configuration.txt";
    static final String HISTORY = "matchbox.history";
    static final String PROJECT_HISTORY = "matchbox.project.history";
    static final String FILESET_HISTORY = "matchbox.fileset.history";
    static final List<String> FILES = List.of(CONFIG, HISTORY, PROJECT_HISTORY, FILESET_HISTORY);

    private final Path legacyDir;
    private final Path legacyLog;

    /** The imported values, and notes for the log. */
    record Imported(SettingsData settings, RecentData recent, List<String> notes) {}

    LegacySettingsImporter(Path home) {
        this.legacyDir = home.resolve(".config").resolve("matchbox");
        this.legacyLog = home.resolve(".matchbox.log");
    }

    Path legacyDir() {
        return legacyDir;
    }

    /** True if a legacy settings or history file exists. */
    boolean hasLegacySettings() {
        return FILES.stream().anyMatch(f -> Files.exists(legacyDir.resolve(f)));
    }

    /** True if any legacy file exists, the old log included. */
    boolean hasLegacyFiles() {
        return hasLegacySettings() || Files.exists(legacyLog);
    }

    Imported read() {
        List<String> notes = new ArrayList<>();
        String charset = null, separators = null, logFile = null;
        List<String> extensions = null, patterns = null;
        Integer maxRecentFiles = null;
        Boolean openLastProject = null;

        for (String line : readLines(legacyDir.resolve(CONFIG), notes)) {
            String value;
            if ((value = valueOf(line, "charset=")) != null) {
                charset = value;
            } else if ((value = valueOf(line, "igt_paragraph_color=")) != null
                || (value = valueOf(line, "lexical_description_color=")) != null) {
                // colours are now display styles of each project
                notes.add("Ignored " + line.strip() + " (colours are set per project, in Project properties)");
            } else if ((value = valueOf(line, "morpheme_separators=")) != null) {
                separators = value;
            } else if ((value = valueOf(line, "file_extension=")) != null) {
                extensions = Arrays.asList(value.split(","));
            } else if ((value = valueOf(line, "file_patterns=")) != null) {
                patterns = List.of(value);
            } else if ((value = valueOf(line, "open_last_project=")) != null) {
                openLastProject = Boolean.valueOf(value);
            } else if ((value = valueOf(line, "max_recent_files=")) != null) {
                try {
                    maxRecentFiles = Integer.valueOf(value);
                } catch (NumberFormatException e) {
                    notes.add("Ignored invalid max_recent_files=" + value);
                }
            } else if ((value = valueOf(line, "logfile=")) != null) {
                Path path = Path.of(value);
                // the old code resolved relative paths against the configuration *file*
                logFile = (path.isAbsolute() ? path : legacyDir.resolve(path)).toString();
            }
        }
        SettingsData settings = new SettingsData(
            SettingsData.CURRENT_VERSION, charset, separators,
            extensions, patterns, maxRecentFiles, null, openLastProject, logFile, null, null, null
        );
        RecentData recent = new RecentData(
            RecentData.CURRENT_VERSION,
            readLines(legacyDir.resolve(HISTORY), notes),
            readLines(legacyDir.resolve(PROJECT_HISTORY), notes)
        );
        return new Imported(settings, recent, notes);
    }

    /**
     * Deletes the legacy files, then the legacy directory if it is empty and is not one of the
     * new directories. The old log is kept if it is the log file chosen by the user.
     *
     * @return what was done, for the log
     */
    List<String> deleteLegacyFiles(AppDirectories directories, Path customLogFile) {
        List<String> messages = new ArrayList<>();
        for (String name : FILES) {
            delete(legacyDir.resolve(name), messages);
        }
        if (customLogFile == null || !same(customLogFile, legacyLog)) {
            delete(legacyLog, messages);
        }
        if (Files.isDirectory(legacyDir)
            && !same(legacyDir, directories.configDir())
            && !same(legacyDir, directories.stateDir())
            && !same(legacyDir, directories.logDir())) {
            try (var content = Files.list(legacyDir)) {
                if (content.findAny().isEmpty()) {
                    Files.delete(legacyDir);
                    messages.add("Deleted the empty directory " + legacyDir);
                }
            } catch (IOException e) {
                messages.add("Could not delete the directory " + legacyDir + ": " + e.getMessage());
            }
        }
        return messages;
    }

    private static void delete(Path file, List<String> messages) {
        try {
            if (Files.deleteIfExists(file)) {
                messages.add("Deleted the old file " + file);
            }
        } catch (IOException e) {
            messages.add("Could not delete the old file " + file + " (retried at next start): " + e.getMessage());
        }
    }

    private static String valueOf(String line, String keyWithEquals) {
        if (!line.startsWith(keyWithEquals)) return null;
        String value = line.substring(keyWithEquals.length()).strip();
        return value.isEmpty() ? null : value;
    }

    private static List<String> readLines(Path file, List<String> notes) {
        if (!Files.exists(file)) return List.of();
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .map(String::strip).filter(l -> !l.isEmpty()).toList();
        } catch (IOException e) {
            notes.add("Could not read the old file " + file + ": " + e.getMessage());
            return List.of();
        }
    }

    private static boolean same(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
