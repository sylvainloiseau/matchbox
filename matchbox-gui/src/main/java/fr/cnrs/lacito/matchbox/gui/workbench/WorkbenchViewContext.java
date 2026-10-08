package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaPlayers;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The {@link ViewContext} of a window: one instance for all the views of all the modes. */
final class WorkbenchViewContext implements ViewContext {

    private final Workbench workbench;

    WorkbenchViewContext(Workbench workbench) {
        this.workbench = workbench;
    }

    @Override
    public void selectInEditor(TextCoordinate span) {
        workbench.navigator().showInEditor(span);
    }

    @Override
    public void select(CorpusObject object) {
        workbench.navigator().reveal(object);
    }

    @Override
    public void browse(CorpusObject object) {
        workbench.navigator().browse(object);
    }

    @Override
    public boolean canBrowse(CorpusObject object) {
        return workbench.navigator().canBrowse(object);
    }

    @Override
    public String highlightColor(CorpusObject object) {
        return workbench.highlightStyles().colorFor(object);
    }

    @Override
    public CorpusView corpus() {
        return workbench.session().corpus();
    }

    @Override
    public boolean canEdit(CorpusObject object) {
        return workbench.edits().canEdit(object);
    }

    @Override
    public EditOutcome setField(CorpusObject object, String fieldName, String value) {
        return workbench.edits().setField(object, fieldName, value);
    }

    @Override
    public EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits) {
        return workbench.edits().applyEdits(object, edits);
    }

    @Override
    public EditOutcome addField(CorpusObject object, String fieldName, String value) {
        return workbench.edits().addField(object, fieldName, value);
    }

    @Override
    public EditOutcome removeField(CorpusObject object, String fieldName) {
        return workbench.edits().removeField(object, fieldName);
    }

    @Override
    public EditOutcome runTextAction(TextAction action) {
        return workbench.focus()
            .map(f -> runTextAction(f.text(), action))
            .orElseGet(() -> new EditOutcome.NotEditable("No text is selected"));
    }

    @Override
    public EditOutcome runTextAction(TextId text, TextAction action) {
        return workbench.edits().runTextAction(text, action);
    }

    @Override
    public Optional<Path> projectDirectory() {
        Project project = workbench.session().project();
        if (project == null || project.fileProperty().get() == null) return Optional.empty();
        return Optional.ofNullable(project.fileProperty().get().toPath().toAbsolutePath().getParent());
    }

    @Override
    public MediaPlayers mediaPlayers() {
        return workbench.mediaPlayers();
    }

    @Override
    public void showDocument(String uri) {
        workbench.showDocument(uri);
    }

    @Override
    public boolean undo(CorpusObject object) {
        return workbench.undo();
    }

    @Override
    public boolean redo(CorpusObject object) {
        return workbench.redo();
    }
}
