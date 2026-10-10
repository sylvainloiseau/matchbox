package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.edit.ViewEditService;
import fr.cnrs.lacito.matchbox.gui.mode.ModeHost;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.gui.view.ObjectViewRegistry;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.media.SharedMediaPlayers;
import fr.cnrs.lacito.matchbox.model.EditJournal;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javafx.application.HostServices;
import javafx.stage.Window;

/**
 * The services of a window, shared by all its modes: the project, the edits and the undo journal,
 * the view context given to object views, the media players, the status line, the navigation
 * between modes, the object view registry, the settings and the assistant. Modes are given the
 * workbench when they are created; they own no service themselves. Closed with the window.
 */
public final class Workbench implements AutoCloseable {

    private final UserSettings settings;
    private final AssistController assist;
    private final Supplier<Window> window;
    private final ProjectSession session;
    private final EditHints editHints = new EditHints();
    private final EditJournalView journal;
    private final ViewEditService edits;
    private final SharedMediaPlayers mediaPlayers = new SharedMediaPlayers();
    private final StatusLine status = new StatusLine();
    private final Navigator navigator;
    private final ObjectViewRegistry viewRegistry;
    private final HighlightStyles highlightStyles;
    private final ViewContext viewContext;
    private final SegmentPlayback playback;
    private final ProjectFiles files;
    private HostServices hostServices;

    /**
     * @param applyExecutor where parse results are applied ({@code Platform::runLater})
     * @param window        owner of the dialogs
     */
    public Workbench(UserSettings settings, AssistController assist, Supplier<Window> window, Executor applyExecutor) {
        this(settings, assist, window, applyExecutor, ObjectViewRegistry.load());
    }

    public Workbench(UserSettings settings, AssistController assist, Supplier<Window> window, Executor applyExecutor,
                     ObjectViewRegistry viewRegistry) {
        this.settings = settings;
        this.assist = assist;
        this.window = window;
        this.session = new ProjectSession(applyExecutor);
        this.journal = new EditJournalView(session);
        this.edits = new ViewEditService(session::project, editHints, settings);
        this.navigator = new Navigator(session);
        this.viewRegistry = viewRegistry;
        this.highlightStyles = new HighlightStyles(session, PluginManager::getPluginById);
        this.viewContext = new WorkbenchViewContext(this);
        this.playback = new SegmentPlayback(viewContext, status);
        this.files = new ProjectFiles(this);
        // the players and the hints belong to the files of the previous project
        session.onProjectChanging(() -> {
            mediaPlayers.closeAll();
            editHints.clear();
            status.clear();
        });
        if (assist != null) {
            session.projectProperty().addListener((obs, o, n) -> assist.setProject(n));
        }
    }

    public ProjectSession session() {
        return session;
    }

    public EditHints editHints() {
        return editHints;
    }

    public EditJournalView journal() {
        return journal;
    }

    public ViewEditService edits() {
        return edits;
    }

    public SharedMediaPlayers mediaPlayers() {
        return mediaPlayers;
    }

    public StatusLine status() {
        return status;
    }

    public Navigator navigator() {
        return navigator;
    }

    public ObjectViewRegistry viewRegistry() {
        return viewRegistry;
    }

    public HighlightStyles highlightStyles() {
        return highlightStyles;
    }

    public ViewContext viewContext() {
        return viewContext;
    }

    /** The files and directories of the project (add, settings, remove). */
    public ProjectFiles files() {
        return files;
    }

    public SegmentPlayback playback() {
        return playback;
    }

    public UserSettings settings() {
        return settings;
    }

    public AssistController assist() {
        return assist;
    }

    /** Owner of the dialogs (may be null before the window is shown). */
    public Window window() {
        return window.get();
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /** Opens a URI in the system browser or the default application. */
    public void showDocument(String uri) {
        if (hostServices != null) {
            hostServices.showDocument(uri);
        }
    }

    /** The modes of the window, once attached (see {@link Navigator#attach}). */
    public Optional<ModeHost> modes() {
        return Optional.ofNullable(navigator.host());
    }

    /** The focus of the active mode: what the window's commands target. */
    public Optional<Focus> focus() {
        return modes().flatMap(ModeHost::focus);
    }

    // ------------------------------------------------------------------
    // Undo journal
    // ------------------------------------------------------------------

    /** Undoes the last change of the project; reports a change the user cannot see. */
    public boolean undo() {
        Optional<EditJournal.Entry> entry = journal.journal().flatMap(EditJournal::undo);
        entry.ifPresent(e -> report("Undone: ", e));
        return entry.isPresent();
    }

    /** Applies again the last undone change of the project. */
    public boolean redo() {
        Optional<EditJournal.Entry> entry = journal.journal().flatMap(EditJournal::redo);
        entry.ifPresent(e -> report("Redone: ", e));
        return entry.isPresent();
    }

    /**
     * A change of several files is reported with a "Save all" link; a change of a file that is not
     * the active mode's focus, with a "Show" link.
     */
    private void report(String prefix, EditJournal.Entry entry) {
        if (entry.parts().size() > 1) {
            status.info(prefix + entry.description() + " (" + entry.where() + ", unsaved)", "Save all", this::saveAll);
            return;
        }
        EditJournal.Part part = entry.parts().get(0);
        boolean visible = focus().map(f -> f.text().equals(part.document().getId())).orElse(false);
        if (visible) {
            status.clear();
        } else {
            status.info(prefix + entry.label(), "Show", () ->
                navigator.showInEditor(Focus.at(part.document().getId(),
                    Math.min(part.firstOffset(), part.document().getCurrentText().length()))));
        }
    }

    /** Saves every document with unsaved changes; failures are shown. */
    public void saveAll() {
        Project project = session.project();
        if (project == null) return;
        try {
            project.saveAll();
            status.clear();
        } catch (IOException e) {
            ErrorReporter.report(window(), "Error saving files", e);
        }
    }

    @Override
    public void close() {
        edits.close();
        mediaPlayers.closeAll();
        if (assist != null) {
            assist.close();
        }
    }
}
