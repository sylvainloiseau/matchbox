package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.CorpusListener;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.ProjectSession;
import fr.cnrs.lacito.matchbox.gui.workbench.SelectionTracker;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

/**
 * Recommended base of a mode: kept alive but cheap while hidden. While the mode is active, the
 * changes of the corpus are given to {@link #onTextsChanged} as they arrive; while it is hidden,
 * they are only accumulated, and given once, merged, on the next activation (a change of project
 * supersedes them: {@link #onProjectChanged}). Provides the mode's {@link SelectionTracker} and
 * focus property.
 */
public abstract class AbstractMode implements Mode {

    protected final Workbench workbench;
    protected final SelectionTracker selection;
    private final ObjectProperty<Optional<Focus>> focus = new SimpleObjectProperty<>(Optional.empty());
    private final ProjectSession session;
    private final Set<TextId> pendingTexts = new LinkedHashSet<>();
    /** True until the content is built for the current project (modes are created after it is open). */
    private boolean projectChanged = true;
    private boolean active;

    private final CorpusListener corpusListener = (id, before, after) -> textChanged(id);

    protected AbstractMode(Workbench workbench) {
        this(workbench, workbench.session(), new SelectionTracker(workbench.editHints()));
    }

    /** For tests: a session and tracker without a whole workbench. */
    protected AbstractMode(Workbench workbench, ProjectSession session, SelectionTracker selection) {
        this.workbench = workbench;
        this.session = session;
        this.selection = selection;
        session.addCorpusListener(corpusListener);
        session.projectProperty().addListener((obs, o, n) -> projectChanged());
    }

    private void textChanged(TextId id) {
        selection.textChanged(id, session.corpus(), active);
        if (active) {
            onTextsChanged(Set.of(id));
        } else if (!projectChanged) {
            pendingTexts.add(id);
        }
    }

    private void projectChanged() {
        selection.set(null);
        pendingTexts.clear();
        if (active) {
            onProjectChanged();
        } else {
            projectChanged = true;
        }
    }

    @Override
    public final void activate() {
        active = true;
        if (projectChanged) {
            projectChanged = false;
            pendingTexts.clear();
            onProjectChanged();
        } else if (!pendingTexts.isEmpty()) {
            Set<TextId> changed = Set.copyOf(pendingTexts);
            pendingTexts.clear();
            onTextsChanged(changed);
        }
        onActivated();
    }

    @Override
    public final void deactivate() {
        active = false;
        onDeactivated();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public ObservableValue<Optional<Focus>> focusProperty() {
        return focus;
    }

    /** Sets what the window's commands target (call it when the user selects something). */
    protected void setFocus(Optional<Focus> f) {
        focus.set(f);
    }

    /** Texts changed (parsed again, added or removed); called only while the mode is active. */
    protected abstract void onTextsChanged(Set<TextId> texts);

    /** The project changed: rebuild everything; called only while the mode is active. */
    protected abstract void onProjectChanged();

    protected void onActivated() {
    }

    protected void onDeactivated() {
    }
}
