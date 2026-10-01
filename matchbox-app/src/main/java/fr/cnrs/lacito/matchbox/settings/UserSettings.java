package fr.cnrs.lacito.matchbox.settings;

import fr.cnrs.lacito.matchbox.model.DocumentOptions;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The user's settings as used by the application: observable properties, saved automatically
 * (debounced) to the per-user directories of the OS ({@link AppDirectories}).
 *
 * <p>Properties are meant to be read and changed on one thread (the JavaFX thread in the GUI).
 * Files are written on a background thread from immutable snapshots; call {@link #close()} on
 * exit to write pending changes.
 */
public final class UserSettings implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(UserSettings.class.getName());

    static final long DEFAULT_SAVE_DELAY_MS = 500;

    /** A message produced while loading, to be logged once logging is set up. */
    public record LoadMessage(Level level, String text) {}

    private final SettingsStore store;
    private final long saveDelayMs;
    private final List<LoadMessage> loadReport;

    private final ObjectProperty<Charset> charset = new SimpleObjectProperty<>();
    private final StringProperty igtColor = new SimpleStringProperty();
    private final StringProperty lexicalColor = new SimpleStringProperty();
    private final StringProperty morphemeSeparators = new SimpleStringProperty();
    private final ObservableList<String> fileExtensions = FXCollections.observableArrayList();
    private final ObservableList<String> filePatterns = FXCollections.observableArrayList();
    private final IntegerProperty maxRecentFiles = new SimpleIntegerProperty();
    private final IntegerProperty maxRecentProjects = new SimpleIntegerProperty();
    private final BooleanProperty openLastProject = new SimpleBooleanProperty();
    private String logFile;
    private final ObjectProperty<AssistSettingsData> assist = new SimpleObjectProperty<>();
    private final RecentItems recentFiles = new RecentItems(maxRecentFiles);
    private final RecentItems recentProjects = new RecentItems(maxRecentProjects);

    /** True while properties are set from code (loading, apply), to save once at the end. */
    private boolean updating;

    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "matchbox-settings");
        t.setDaemon(true);
        return t;
    });
    private final Object pendingLock = new Object();
    private final Object writeLock = new Object();
    private SettingsData pendingSettings;
    private RecentData pendingRecent;
    private ScheduledFuture<?> scheduled;

    private UserSettings(SettingsStore store, SettingsData settings, RecentData recent,
                         List<LoadMessage> loadReport, long saveDelayMs) {
        this.store = store;
        this.saveDelayMs = saveDelayMs;
        this.loadReport = List.copyOf(loadReport);
        updating = true;
        set(settings);
        recentFiles.setAllFromStrings(recent.recentFiles());
        recentProjects.setAllFromStrings(recent.recentProjects());
        updating = false;

        for (Observable o : List.<Observable>of(charset, igtColor, lexicalColor, morphemeSeparators,
                fileExtensions, filePatterns, maxRecentFiles, maxRecentProjects, openLastProject, assist)) {
            o.addListener(obs -> settingsChanged());
        }
        recentFiles.items().addListener((Observable obs) -> recentChanged());
        recentProjects.items().addListener((Observable obs) -> recentChanged());
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Loads the settings of the current user, importing those of older versions if needed. */
    public static UserSettings load(AppDirectories directories) {
        return load(directories, Path.of(System.getProperty("user.home")), DEFAULT_SAVE_DELAY_MS);
    }

    static UserSettings load(AppDirectories directories, Path home, long saveDelayMs) {
        SettingsStore store = new SettingsStore(directories);
        LegacySettingsImporter legacy = new LegacySettingsImporter(home);
        List<LoadMessage> report = new ArrayList<>();
        SettingsData settings;
        RecentData recent;

        boolean hasNewFiles = store.hasSettingsFile() || store.hasRecentFile();
        if (!hasNewFiles && legacy.hasLegacySettings()) {
            LegacySettingsImporter.Imported imported = legacy.read();
            imported.notes().forEach(n -> report.add(new LoadMessage(Level.INFO, n)));
            settings = imported.settings().validated(p -> report.add(new LoadMessage(Level.WARNING, p)));
            recent = imported.recent().validated();
            try {
                store.saveSettings(settings);
                store.saveRecent(recent);
                report.add(new LoadMessage(Level.INFO, "Settings imported from " + legacy.legacyDir() +
                    " to " + store.settingsFile() + " and " + store.recentFile()));
                deleteLegacy(legacy, directories, settings, report);
            } catch (IOException e) {
                report.add(new LoadMessage(Level.WARNING, "Could not write the imported settings (" +
                    e.getMessage() + "); the old files are kept and the import will be retried"));
            }
        } else {
            SettingsStore.Loaded<SettingsData> s = store.loadSettings();
            SettingsStore.Loaded<RecentData> r = store.loadRecent();
            s.problems().forEach(p -> report.add(new LoadMessage(Level.WARNING, p)));
            r.problems().forEach(p -> report.add(new LoadMessage(Level.WARNING, p)));
            settings = s.value();
            recent = r.value();
            if (legacy.hasLegacyFiles()) {
                // a previous deletion failed, an older version recreated them, or only the old log is left
                deleteLegacy(legacy, directories, settings, report);
            }
        }
        return new UserSettings(store, settings, recent, report, saveDelayMs);
    }

    private static void deleteLegacy(LegacySettingsImporter legacy, AppDirectories directories,
                                     SettingsData settings, List<LoadMessage> report) {
        Path custom = resolveLogFile(directories, settings.logFile());
        legacy.deleteLegacyFiles(directories, custom)
            .forEach(m -> report.add(new LoadMessage(m.startsWith("Could not") ? Level.WARNING : Level.INFO, m)));
    }

    /** Messages produced while loading (import, deleted files, unreadable or invalid values). */
    public List<LoadMessage> getLoadReport() {
        return loadReport;
    }

    /** Problems the user should be told about (unreadable files, invalid values, failed import). */
    public List<String> getLoadWarnings() {
        return loadReport.stream().filter(m -> m.level() == Level.WARNING).map(LoadMessage::text).toList();
    }

    // ------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------

    public ObjectProperty<Charset> charsetProperty() { return charset; }
    public StringProperty igtColorProperty() { return igtColor; }
    public StringProperty lexicalColorProperty() { return lexicalColor; }
    public StringProperty morphemeSeparatorsProperty() { return morphemeSeparators; }
    /** Normalised extensions (lower case, without dot); empty means all files. */
    public ObservableList<String> fileExtensions() { return fileExtensions; }
    public ObservableList<String> filePatterns() { return filePatterns; }
    public IntegerProperty maxRecentFilesProperty() { return maxRecentFiles; }
    public IntegerProperty maxRecentProjectsProperty() { return maxRecentProjects; }
    public BooleanProperty openLastProjectProperty() { return openLastProject; }
    /** Settings of the AI assistant (an immutable value: set a modified copy to change them). */
    public ObjectProperty<AssistSettingsData> assistProperty() { return assist; }
    public RecentItems recentFiles() { return recentFiles; }
    public RecentItems recentProjects() { return recentProjects; }

    public AppDirectories directories() {
        return store.directories();
    }

    /** Log file chosen by the user (relative paths are resolved against the config directory). */
    public Optional<Path> customLogFile() {
        return Optional.ofNullable(resolveLogFile(directories(), logFile));
    }

    private static Path resolveLogFile(AppDirectories directories, String logFile) {
        if (logFile == null) return null;
        Path p = Path.of(logFile);
        return p.isAbsolute() ? p : directories.configDir().resolve(p);
    }

    /** Live view of the charset and extension settings, for projects. */
    public DocumentOptions documentOptions() {
        return new DocumentOptions() {
            public Charset charset() { return charset.get(); }
            public List<String> fileExtensions() { return List.copyOf(fileExtensions); }
        };
    }

    /** The current values, e.g. for a dialog that edits a copy. */
    public SettingsData snapshot() {
        return new SettingsData(
            SettingsData.CURRENT_VERSION,
            charset.get().name(),
            igtColor.get(),
            lexicalColor.get(),
            morphemeSeparators.get(),
            List.copyOf(fileExtensions),
            List.copyOf(filePatterns),
            maxRecentFiles.get(),
            maxRecentProjects.get(),
            openLastProject.get(),
            logFile,
            assist.get()
        );
    }

    /** Replaces all values (invalid ones by defaults); saved once. */
    public void apply(SettingsData edited) {
        updating = true;
        try {
            set(edited.validated(p -> logger.warning("Settings: " + p)));
        } finally {
            updating = false;
        }
        settingsChanged();
    }

    private void set(SettingsData d) {
        charset.set(d.charsetValue());
        igtColor.set(d.igtColor());
        lexicalColor.set(d.lexicalColor());
        morphemeSeparators.set(d.morphemeSeparators());
        if (!fileExtensions.equals(d.fileExtensions())) fileExtensions.setAll(d.fileExtensions());
        if (!filePatterns.equals(d.filePatterns())) filePatterns.setAll(d.filePatterns());
        maxRecentFiles.set(d.maxRecentFiles());
        maxRecentProjects.set(d.maxRecentProjects());
        openLastProject.set(d.openLastProject());
        logFile = d.logFile();
        assist.set(d.assist());
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    private void settingsChanged() {
        if (updating) return;
        SettingsData snapshot = snapshot();
        synchronized (pendingLock) {
            pendingSettings = snapshot;
            scheduleSave();
        }
    }

    private void recentChanged() {
        if (updating) return;
        RecentData snapshot = new RecentData(RecentData.CURRENT_VERSION, recentFiles.toStrings(), recentProjects.toStrings());
        synchronized (pendingLock) {
            pendingRecent = snapshot;
            scheduleSave();
        }
    }

    private void scheduleSave() {
        if ((scheduled == null || scheduled.isDone()) && !saver.isShutdown()) {
            scheduled = saver.schedule(this::writePending, saveDelayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void writePending() {
        synchronized (writeLock) {
            SettingsData settings;
            RecentData recent;
            synchronized (pendingLock) {
                settings = pendingSettings;
                recent = pendingRecent;
                pendingSettings = null;
                pendingRecent = null;
            }
            try {
                if (settings != null) store.saveSettings(settings);
                if (recent != null) store.saveRecent(recent);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not save the settings", e);
            }
        }
    }

    /** Writes pending changes now, on the calling thread. */
    public void flush() {
        synchronized (pendingLock) {
            if (scheduled != null) {
                scheduled.cancel(false);
                scheduled = null;
            }
        }
        writePending();
    }

    /** Writes pending changes and stops the background saver. */
    @Override
    public void close() {
        flush();
        saver.shutdown();
    }
}
