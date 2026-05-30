package fr.cnrs.lacito.matchbox.model;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages application configuration and history
 */
public class ApplicationSettings {

    private static final Logger LOG = Logger.getLogger(
        ApplicationSettings.class.getName()
    );

    private static final String LOG_FILE_URI_CONF = "logfile";
    private static final String LOG_FILE_URI_DEFAULT = ".notebook.log";
    private static final String CONFIG_DIR = ".config/notebook";
    private static final String HISTORY_FILE = "fieldnode.history";
    private static final String HISTORY_FILESET_FILE = "fileset.history";
    private static final String HISTORY_PROJECT_FILE = "project.history";
    private static final String CONFIG_FILE = "configuration.txt";

    private Path configPath;
    private Path historyPath;
    private Path historyFileSetPath;
    private Path historyProjectPath;
    private Path configFilePath;
    private Charset currentCharset;
    private List<String> recentFiles;
    private List<String> recentFileSets;
    private List<String> recentProjects;
    private String igtParagraphColor;
    private String morphemeSeparators;
    private String lexicalDescriptionColor;
    private String fileExtensions;
    private String filePatterns;
    private int maxRecentFiles;
    private int maxRecentProjects;
    private Path logFileUri = null;
    private boolean non_default_log_file = false;
    private boolean openLastProject = false;

    private int maxRecentProject = 10;

    public ApplicationSettings() {
        Path homeDir = Paths.get(System.getProperty("user.home"));
        this.configPath = homeDir.resolve(CONFIG_DIR);
        this.historyPath = configPath.resolve(HISTORY_FILE);
        this.historyFileSetPath = configPath.resolve(HISTORY_FILESET_FILE);
        this.historyProjectPath = configPath.resolve(HISTORY_PROJECT_FILE);
        this.configFilePath = configPath.resolve(CONFIG_FILE);
        this.currentCharset = StandardCharsets.UTF_8;
        this.igtParagraphColor = "#000000";
        this.lexicalDescriptionColor = "#000000";
        this.morphemeSeparators = "-,/";
        this.fileExtensions = "txt,md";
        this.filePatterns = "";
        this.maxRecentFiles = 10;
        this.openLastProject = false;

        // Create config directory if it doesn't exist
        try {
            Files.createDirectories(configPath);
        } catch (IOException e) {
            System.err.println(
                "Failed to create config directory: " + e.getMessage()
            );
        }

        loadConfiguration();
        if (logFileUri == null) {
            logFileUri = homeDir.resolve(LOG_FILE_URI_DEFAULT);
        }
        this.recentFiles = loadHistory(this.historyPath);
        this.recentFileSets = loadHistory(this.historyFileSetPath);
        this.recentProjects = loadHistory(this.historyProjectPath);
    }

    private void loadConfiguration() {
        if (Files.exists(configFilePath)) {
            try {
                List<String> lines = Files.readAllLines(configFilePath);
                for (String line : lines) {
                    if (line.startsWith("charset=")) {
                        String charsetName = line
                            .substring("charset=".length())
                            .trim();
                        try {
                            currentCharset = Charset.forName(charsetName);
                        } catch (Exception e) {
                            currentCharset = StandardCharsets.UTF_8;
                        }
                    } else if (line.startsWith(LOG_FILE_URI_CONF)) {
                        String uri = line
                            .substring(LOG_FILE_URI_CONF.length() + 1)
                            .trim();
                        if (!uri.isEmpty()) {
                            logFileUri = Path.of(uri);
                            if (!logFileUri.isAbsolute()) {
                                logFileUri = configFilePath.resolve(logFileUri);
                            }
                            if (!Files.isRegularFile(logFileUri)) {
                                Files.createDirectories(logFileUri.getParent());
                            }
                            if (!Files.isWritable(logFileUri)) {
                                throw new IllegalStateException(
                                    "The Log file specified in the configuration file is not writable: " +
                                        logFileUri
                                );
                            }
                            non_default_log_file = true;
                        }
                    } else if (line.startsWith("igt_paragraph_color=")) {
                        String color = line
                            .substring("igt_paragraph_color=".length())
                            .trim();
                        if (!color.isEmpty()) {
                            igtParagraphColor = color;
                        }
                    } else if (line.startsWith("lexical_description_color=")) {
                        String color = line
                            .substring("lexical_description_color=".length())
                            .trim();
                        if (!color.isEmpty()) {
                            lexicalDescriptionColor = color;
                        }
                    } else if (line.startsWith("morpheme_separators=")) {
                        String sep = line
                            .substring("morpheme_separators=".length())
                            .trim();
                        if (!sep.isEmpty()) {
                            morphemeSeparators = sep;
                        }
                    } else if (line.startsWith("file_extension=")) {
                        String fe = line
                            .substring("file_extension=".length())
                            .trim();
                        if (!fe.isEmpty()) {
                            fileExtensions = fe;
                        }
                    } else if (line.startsWith("open_last_project=")) {
                        Boolean olp = Boolean.valueOf(
                            line.substring("open_last_project=".length()).trim()
                        );
                        if (olp) {
                            this.openLastProject = olp.booleanValue();
                        }
                    } else if (line.startsWith("file_patterns=")) {
                        String fp = line
                            .substring("file_patterns=".length())
                            .trim();
                        if (!fp.isEmpty()) {
                            filePatterns = fp;
                        }
                    } else if (line.startsWith("max_recent_files=")) {
                        try {
                            int max = Integer.parseInt(
                                line
                                    .substring("max_recent_files=".length())
                                    .trim()
                            );
                            if (max > 0) {
                                maxRecentFiles = max;
                            }
                        } catch (NumberFormatException ex) {
                            maxRecentFiles = 10;
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println(
                    "Failed to load configuration: " + e.getMessage()
                );
            }
        }
    }

    private List<String> loadHistory(Path historyPath) {
        if (Files.exists(historyPath)) {
            try {
                List<String> lines = Files.readAllLines(historyPath);
                LOG.info("Successfully read history file.");
                return lines;
            } catch (IOException e) {
                LOG.warning("unable to read history file:" + e.getMessage());
                System.err.println("Failed to load history: " + e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    public void saveConfiguration() {
        try {
            List<String> lines = new ArrayList<>();
            if (non_default_log_file) lines.add(
                LOG_FILE_URI_CONF + "=" + logFileUri.toString()
            );
            lines.add("charset=" + currentCharset.name());
            lines.add("igt_paragraph_color=" + igtParagraphColor);
            lines.add("lexical_description_color=" + lexicalDescriptionColor);
            lines.add("morpheme_separators=" + morphemeSeparators);
            lines.add("file_extension=" + fileExtensions);
            lines.add("file_patterns=" + filePatterns);
            lines.add("max_recent_files=" + maxRecentFiles);
            Files.write(configFilePath, lines);
        } catch (IOException e) {
            System.err.println(
                "Failed to save configuration: " + e.getMessage()
            );
        }
    }

    public void addToProjectHistory(String filePath) {
        recentProjects.remove(filePath); // Remove if already exists
        recentProjects.add(0, filePath); // Add to top

        // Keep only up to maxRecentFiles entries
        if (recentProjects.size() > maxRecentProject) {
            recentProjects = recentProjects.subList(0, maxRecentProjects);
        }
        saveProjectHistory();
    }

    private void saveHistory() {
        try {
            Files.write(historyPath, recentFiles);
        } catch (IOException e) {
            System.err.println("Failed to save history: " + e.getMessage());
        }
    }

    public void addToHistory(String filePath) {
        recentFiles.remove(filePath); // Remove if already exists
        recentFiles.add(0, filePath); // Add to top

        // Keep only up to maxRecentFiles entries
        if (recentFiles.size() > maxRecentFiles) {
            recentFiles = recentFiles.subList(0, maxRecentFiles);
        }

        saveHistory();
    }

    public List<String> getRecentFiles() {
        return recentFiles;
    }

    public Path getLogFile() {
        return logFileUri;
    }

    public Charset getCurrentCharset() {
        return currentCharset;
    }

    public void setCurrentCharset(Charset charset) {
        this.currentCharset = charset;
        saveConfiguration();
    }

    public String getIgtParagraphColor() {
        return igtParagraphColor;
    }

    public void setIgtParagraphColor(String colorHex) {
        this.igtParagraphColor = colorHex;
        saveConfiguration();
    }

    public String getMorphemeSeparators() {
        return morphemeSeparators;
    }

    public void setMorphemeSeparators(String separators) {
        this.morphemeSeparators = separators;
        saveConfiguration();
    }

    public String getLexicalDescriptionColor() {
        return lexicalDescriptionColor;
    }

    public void setLexicalDescriptionColor(String colorHex) {
        this.lexicalDescriptionColor = colorHex;
        saveConfiguration();
    }

    public String getFileExtensions() {
        return fileExtensions;
    }

    public void setFileExtensions(String fileExtensions) {
        this.fileExtensions = fileExtensions;
        saveConfiguration();
    }

    public String getFilePatterns() {
        return filePatterns;
    }

    public void setFilePatterns(String filePatterns) {
        this.filePatterns = filePatterns;
        saveConfiguration();
    }

    public List<Charset> getAvailableCharsets() {
        return new ArrayList<>(Charset.availableCharsets().values());
    }

    public boolean isOpenLastProject() {
        return openLastProject;
    }

    public void isOpenLastProject(boolean openLastProject) {
        this.openLastProject = openLastProject;
    }

    public void clearRecentFiles() {
        recentFiles.clear();
        saveHistory();
    }

    public int getMaxRecentFiles() {
        return maxRecentFiles;
    }

    public void setMaxRecentFiles(int maxRecentFiles) {
        if (maxRecentFiles > 0) {
            this.maxRecentFiles = maxRecentFiles;
        }
    }

    public List<String> getRecentFileSets() {
        return recentFileSets;
    }

    public List<String> getRecentProjects() {
        return recentProjects;
    }

    public void clearRecentFilesets() {
        recentFileSets.clear();
        saveFilesetHistory();
    }

    public void clearRecentProjects() {
        recentProjects.clear();
        saveProjectHistory();
    }

    private void saveProjectHistory() {
        try {
            Files.write(historyProjectPath, recentProjects);
        } catch (IOException e) {
            System.err.println(
                "Failed to save project history: " + e.getMessage()
            );
        }
    }

    private void saveFilesetHistory() {
        try {
            Files.write(historyFileSetPath, recentFileSets);
        } catch (IOException e) {
            System.err.println(
                "Failed to save fileset history: " + e.getMessage()
            );
        }
    }
}
