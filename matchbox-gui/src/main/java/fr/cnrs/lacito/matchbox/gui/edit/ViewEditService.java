package fr.cnrs.lacito.matchbox.gui.edit;

import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.TextHistory;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Applies the edits object views ask for (see {@link fr.cnrs.lacito.matchbox.grammarfx.ViewContext}).
 *
 * <p>Edits go to the object's {@link TextDocument}, displayed in the editor or not: one undoable
 * change ({@link EditOrigin.Kind#VIEW}), after which the project parses the document again and
 * the views are refreshed. Corpus objects are never changed.
 */
public final class ViewEditService {

    private final Supplier<Project> project;
    private final EditorController editor;
    private final UserSettings settings;
    private final EditValidator validator = new EditValidator();

    public ViewEditService(Supplier<Project> project, EditorController editor, UserSettings settings) {
        this.project = project;
        this.editor = editor;
        this.settings = settings;
    }

    private ViewEditPlanner planner() {
        Project p = project.get();
        boolean validate = !settings.allowInvalidViewEditsProperty().get();
        return new ViewEditPlanner(p.getCorpus(), p::document,
            d -> p.getGrammarsForFile(d.getFilePath()), validate ? validator : null);
    }

    /**
     * True if the object's text is in the project, its file writable and the object has fields
     * (possibly none yet: entries can be added to an empty metadata block).
     */
    public boolean canEdit(CorpusObject object) {
        Project p = project.get();
        if (p == null || !(object instanceof HasFields)) {
            return false;
        }
        return p.getCorpus().textOf(object)
            .flatMap(t -> p.document(t.id()))
            .map(d -> Files.isWritable(d.getFilePath()))
            .orElse(false);
    }

    public EditOutcome setField(CorpusObject object, String fieldName, String value) {
        return edit(object, e -> e.setField(object, fieldName, value, contentOf(object)));
    }

    public EditOutcome addField(CorpusObject object, String fieldName, String value) {
        return edit(object, e -> e.addField(object, fieldName, value, contentOf(object)));
    }

    public EditOutcome removeField(CorpusObject object, String fieldName) {
        return edit(object, e -> e.removeField(object, fieldName, contentOf(object)));
    }

    private String contentOf(CorpusObject object) {
        return project.get().getCorpus().textOf(object).map(Text::content).orElse("");
    }

    /** Asks the object's grammar for the edits, then applies them. */
    private EditOutcome edit(CorpusObject object, Function<ObjectEditor, EditProposal> request) {
        Project p = project.get();
        Optional<Text> text = p.getCorpus().textOf(object);
        if (text.isEmpty()) {
            return new EditOutcome.Stale("The text changed: the object no longer exists");
        }
        Optional<GrammarPlugin> grammar = text.get().grammarIdOf(object).flatMap(id ->
            p.document(text.get().id()).flatMap(d -> p.getGrammarsForFile(d.getFilePath()).stream()
                .filter(g -> g.getId().equals(id)).findFirst()));
        if (grammar.isEmpty()) {
            return new EditOutcome.NotEditable("The grammar of this object is no longer applied to the file");
        }
        EditProposal proposal;
        try {
            proposal = request.apply(grammar.get().getObjectEditor());
        } catch (RuntimeException | LinkageError e) {
            return new EditOutcome.NotEditable("The grammar failed to write the value: " + e);
        }
        return switch (proposal) {
            case EditProposal.Refused r -> new EditOutcome.Refused(r.reason());
            case EditProposal.Edits e -> applyEdits(object, e.edits());
        };
    }

    public EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits) {
        if (project.get() == null) {
            return new EditOutcome.NotEditable("No project");
        }
        // views building their own edits need no fields, only a document that can be written
        return switch (planner().prepare(object, edits)) {
            case ViewEditPlanner.Failed f -> f.outcome();
            case ViewEditPlanner.Ready r -> apply(r);
        };
    }

    private EditOutcome apply(ViewEditPlanner.Ready ready) {
        if (ready.edits().isEmpty()) {
            return new EditOutcome.Applied();
        }
        TextDocument document = ready.document();
        if (!Files.isWritable(document.getFilePath())) {
            return new EditOutcome.NotEditable("The file " + document.getFilePath() + " cannot be written");
        }
        int offset = ready.edits().stream().mapToInt(TextEdit::start).min().orElse(0);
        editor.rememberEdit(document.getId(), offset);
        if (!document.applyEdits(ready.edits(), EditOrigin.of(EditOrigin.Kind.VIEW, this))) {
            return new EditOutcome.Stale("The text changed: parse it again and retry");
        }
        // the project parses the document again (non-typing change)
        return new EditOutcome.Applied();
    }

    /**
     * Applies the edits of a text action to a document and selects the object at
     * {@link TextAction#selectOffset()} once the text is parsed again.
     */
    public EditOutcome runTextAction(TextId text, TextAction action) {
        if (project.get() == null) {
            return new EditOutcome.NotEditable("No project");
        }
        return switch (planner().prepare(text, action.edits())) {
            case ViewEditPlanner.Failed f -> f.outcome();
            case ViewEditPlanner.Ready r -> {
                EditOutcome outcome = apply(r);
                if (outcome.isApplied() && action.selectOffset() >= 0) {
                    editor.selectAfterParse(text, action.selectOffset());
                }
                yield outcome;
            }
        };
    }

    public boolean undo(CorpusObject object) {
        return history(object).map(TextHistory::undo).orElse(false);
    }

    public boolean redo(CorpusObject object) {
        return history(object).map(TextHistory::redo).orElse(false);
    }

    private Optional<TextHistory> history(CorpusObject object) {
        Project p = project.get();
        return p == null ? Optional.empty() : p.history(object.getTextCoordinate().text());
    }

    public void close() {
        validator.close();
    }
}
