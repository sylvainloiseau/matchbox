package fr.cnrs.lacito.matchbox.gui.close;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.io.IOException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Asks what to save before a project is closed (Close project, New project, Open project, Quit),
 * then saves it: the definition first, then the chosen files. Nothing is asked when nothing would
 * be lost. The dialog, the saving of the definition and the error display are given, for tests.
 */
public final class ProjectCloser {

    /** Shows the question; empty to cancel. */
    @FunctionalInterface
    public interface Asker {
        Optional<CloseChoice> ask(UnsavedProjectState state, CloseAction action);
    }

    private final Asker asker;
    private final Predicate<Project> saveDefinition;
    private final BiConsumer<String, Exception> errors;

    /**
     * @param saveDefinition saves the definition (asks for a file if the project has none); false
     *                       if it was not saved (failure or cancelled)
     * @param errors         shows a failure to save a file (title, error)
     */
    public ProjectCloser(Asker asker, Predicate<Project> saveDefinition, BiConsumer<String, Exception> errors) {
        this.asker = asker;
        this.saveDefinition = saveDefinition;
        this.errors = errors;
    }

    /**
     * @return true to go on (what was chosen is saved, the rest discarded), false to keep the
     * project open (cancelled, or a save failed)
     */
    public boolean confirm(Project project, CloseAction action) {
        if (project == null) return true;
        UnsavedProjectState state = UnsavedProjectState.of(project);
        if (state.isEmpty()) return true;
        Optional<CloseChoice> choice = asker.ask(state, action);
        if (choice.isEmpty()) return false;
        if (choice.get().saveDefinition() && !saveDefinition.test(project)) return false;
        for (TextDocument d : choice.get().toSave()) {
            try {
                d.writeFile();
            } catch (IOException e) {
                errors.accept("Error saving " + d.getFilePath(), e);
                return false;
            }
        }
        return true;
    }
}
