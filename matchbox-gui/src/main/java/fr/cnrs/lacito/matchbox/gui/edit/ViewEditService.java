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
import fr.cnrs.lacito.matchbox.gui.workbench.EditHints;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
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
 * change ({@link EditOrigin.Kind#VIEW}, one entry of the project's undo journal, with a
 * description), after which the project parses the document again and the views are refreshed.
 * Corpus objects are never changed. One instance per window, shared by all modes.
 */
public final class ViewEditService {

    private final Supplier<Project> project;
    private final EditHints hints;
    private final UserSettings settings;
    private final EditValidator validator = new EditValidator();

    public ViewEditService(Supplier<Project> project, EditHints hints, UserSettings settings) {
        this.project = project;
        this.hints = hints;
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
        return edit(object, "Edit " + describe(fieldName), e -> e.setField(object, fieldName, value, contentOf(object)));
    }

    public EditOutcome addField(CorpusObject object, String fieldName, String value) {
        return edit(object, "Add " + describe(fieldName), e -> e.addField(object, fieldName, value, contentOf(object)));
    }

    public EditOutcome removeField(CorpusObject object, String fieldName) {
        return edit(object, "Remove " + describe(fieldName), e -> e.removeField(object, fieldName, contentOf(object)));
    }

    /** Result of {@link #setFields}. */
    public record BatchResult(boolean applied, int changes, int files, List<String> problems) {}

    /**
     * Sets fields of several objects, possibly in several files, all or nothing: one change per
     * document, all of them one entry of the undo journal described by {@code description}. If
     * anything cannot be changed (stale text, refused value, read-only file, new parse errors),
     * nothing is applied and the result lists every problem.
     */
    public BatchResult setFields(List<FieldChange> changes, String description) {
        Project p = project.get();
        if (p == null) {
            return new BatchResult(false, 0, 0, List.of("No project"));
        }
        return switch (batch(p).plan(changes)) {
            case BatchEditPlanner.Failed f -> new BatchResult(false, 0, 0, f.problems());
            case BatchEditPlanner.Ready r -> apply(p, r, changes.size(), description);
        };
    }

    /** What would prevent {@link #setFields} from applying these changes; empty if nothing. */
    public List<String> checkFields(List<FieldChange> changes) {
        Project p = project.get();
        if (p == null) return List.of("No project");
        return batch(p).plan(changes) instanceof BatchEditPlanner.Failed f ? f.problems() : List.of();
    }

    private BatchEditPlanner batch(Project p) {
        return new BatchEditPlanner(p.getCorpus(), planner(), (text, object) -> grammarOf(p, text, object),
            d -> Files.isWritable(d.getFilePath()));
    }

    private BatchResult apply(Project p, BatchEditPlanner.Ready ready, int count, String description) {
        boolean[] ok = {true};
        p.journal().grouped(description, () -> {
            for (ViewEditPlanner.Ready document : ready.documents()) {
                if (!ok[0] || document.edits().isEmpty()) continue;
                int offset = document.edits().stream().mapToInt(TextEdit::start).min().orElse(0);
                hints.lastEdit(document.document().getId(), offset);
                ok[0] = document.document().applyEdits(document.edits(), EditOrigin.of(EditOrigin.Kind.VIEW, this, description));
            }
        });
        if (!ok[0]) {
            // a document changed between the check and the change: revert what was applied
            p.journal().undo();
            return new BatchResult(false, 0, 0, List.of("The text changed: parse it again and retry"));
        }
        long files = ready.documents().stream().filter(d -> !d.edits().isEmpty()).count();
        return new BatchResult(true, count, (int) files, List.of());
    }

    private static Optional<GrammarPlugin> grammarOf(Project p, Text text, CorpusObject object) {
        return text.grammarIdOf(object).flatMap(id ->
            p.document(text.id()).flatMap(d -> p.getGrammarsForFile(d.getFilePath()).stream()
                .filter(g -> g.getId().equals(id)).findFirst()));
    }

    /** A field name for the user: "freeTranslation" → "free translation". */
    static String describe(String fieldName) {
        return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
    }

    private String contentOf(CorpusObject object) {
        return project.get().getCorpus().textOf(object).map(Text::content).orElse("");
    }

    /** Asks the object's grammar for the edits, then applies them. */
    private EditOutcome edit(CorpusObject object, String description, Function<ObjectEditor, EditProposal> request) {
        Project p = project.get();
        Optional<Text> text = p.getCorpus().textOf(object);
        if (text.isEmpty()) {
            return new EditOutcome.Stale("The text changed: the object no longer exists");
        }
        Optional<GrammarPlugin> grammar = grammarOf(p, text.get(), object);
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
            case EditProposal.Edits e -> applyEdits(object, e.edits(), description);
        };
    }

    public EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits) {
        return applyEdits(object, edits, "Edit " + object.getLabel());
    }

    /** Applies edits computed by a view; {@code description} names the change in the undo journal. */
    public EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits, String description) {
        if (project.get() == null) {
            return new EditOutcome.NotEditable("No project");
        }
        // views building their own edits need no fields, only a document that can be written
        return switch (planner().prepare(object, edits)) {
            case ViewEditPlanner.Failed f -> f.outcome();
            case ViewEditPlanner.Ready r -> apply(r, description);
        };
    }

    private EditOutcome apply(ViewEditPlanner.Ready ready, String description) {
        if (ready.edits().isEmpty()) {
            return new EditOutcome.Applied();
        }
        TextDocument document = ready.document();
        if (!Files.isWritable(document.getFilePath())) {
            return new EditOutcome.NotEditable("The file " + document.getFilePath() + " cannot be written");
        }
        int offset = ready.edits().stream().mapToInt(TextEdit::start).min().orElse(0);
        hints.lastEdit(document.getId(), offset);
        if (!document.applyEdits(ready.edits(), EditOrigin.of(EditOrigin.Kind.VIEW, this, description))) {
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
                EditOutcome outcome = apply(r, action.label());
                if (outcome.isApplied() && action.selectOffset() >= 0) {
                    hints.selectAfterParse(text, action.selectOffset());
                }
                yield outcome;
            }
        };
    }

    public void close() {
        validator.close();
    }
}
