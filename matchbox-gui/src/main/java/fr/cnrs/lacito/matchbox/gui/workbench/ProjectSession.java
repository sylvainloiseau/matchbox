package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.CorpusListener;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * The project of a window, shared by all modes. Modes listen to {@link #projectProperty()} instead
 * of being given the project, and to {@link #addCorpusListener} for the changes of its texts: these
 * listeners follow the project when it changes. The window may have no project
 * ({@link #closeProject()}): {@link #project()} is then null and {@link #projectOpenProperty()} false.
 */
public final class ProjectSession {

    private final ReadOnlyObjectWrapper<Project> project = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper projectOpen = new ReadOnlyBooleanWrapper(false);
    private final Executor applyExecutor;
    private final List<CorpusListener> corpusListeners = new CopyOnWriteArrayList<>();
    private final CorpusListener forwarder = this::forward;
    /** Run when the project changes, before the property (e.g. closing the media players). */
    private final List<Runnable> beforeChange = new CopyOnWriteArrayList<>();

    /**
     * @param applyExecutor where parse results are applied to the corpus (the JavaFX thread in
     *                      the application), so that corpus listeners run there
     */
    public ProjectSession(Executor applyExecutor) {
        this.applyExecutor = Objects.requireNonNull(applyExecutor);
    }

    /** Makes {@code newProject} the project of the window. */
    public void setProject(Project newProject) {
        Objects.requireNonNull(newProject, "project");
        change(newProject);
    }

    /** Leaves the window without project (the caller has asked about unsaved changes). */
    public void closeProject() {
        change(null);
    }

    private void change(Project newProject) {
        Project old = project.get();
        if (old == newProject) return;
        beforeChange.forEach(Runnable::run);
        if (old != null) {
            old.getCorpus().removeListener(forwarder);
        }
        if (newProject != null) {
            newProject.setApplyExecutor(applyExecutor);
            newProject.getCorpus().addListener(forwarder);
        }
        project.set(newProject);
        projectOpen.set(newProject != null);
    }

    /** Called when the project is about to change (before the listeners of the property). */
    public void onProjectChanging(Runnable action) {
        beforeChange.add(action);
    }

    public ReadOnlyObjectProperty<Project> projectProperty() {
        return project.getReadOnlyProperty();
    }

    /** True while a project is open. */
    public ReadOnlyBooleanProperty projectOpenProperty() {
        return projectOpen.getReadOnlyProperty();
    }

    public boolean isProjectOpen() {
        return projectOpen.get();
    }

    /** The project of the window, or null if none is open. */
    public Project project() {
        return project.get();
    }

    /** The texts of the current project. */
    public CorpusView corpus() {
        Project p = project.get();
        return p == null ? EMPTY : p.getCorpus();
    }

    /**
     * Listens to the changes of the texts of the current project, and of the next ones. On a change
     * of project no per-text event is sent: listen to {@link #projectProperty()} to reset.
     */
    public void addCorpusListener(CorpusListener listener) {
        corpusListeners.add(listener);
    }

    public void removeCorpusListener(CorpusListener listener) {
        corpusListeners.remove(listener);
    }

    private void forward(TextId id, Optional<Text> before, Optional<Text> after) {
        for (CorpusListener l : corpusListeners) {
            l.textChanged(id, before, after);
        }
    }

    private static final CorpusView EMPTY = new fr.cnrs.lacito.matchbox.corpus.Corpus();
}
