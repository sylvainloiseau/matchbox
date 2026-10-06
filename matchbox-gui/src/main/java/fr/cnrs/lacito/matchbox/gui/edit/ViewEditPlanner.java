package fr.cnrs.lacito.matchbox.gui.edit;

import fr.cnrs.lacito.matchbox.assist.suggestion.Relocation;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Prepares the edits a view asks for, without applying them: finds the object's text and
 * document, adapts the edits to the current text of the document (which may have changed since
 * the object was parsed) and, if requested, refuses edits that add parse errors.
 *
 * <p>No JavaFX controls: tested without a display.
 */
public final class ViewEditPlanner {

    /** Result of {@link #prepare}. */
    public sealed interface Prepared {}

    /** Edits ready to be applied to the current text of {@code document}. */
    public record Ready(TextDocument document, List<TextEdit> edits) implements Prepared {}

    /** The edits cannot be applied; nothing was changed. */
    public record Failed(EditOutcome outcome) implements Prepared {}

    private final CorpusView corpus;
    private final Function<TextId, Optional<TextDocument>> documents;
    private final Function<TextDocument, List<GrammarPlugin>> grammars;
    private final EditValidator validator;

    /**
     * @param validator used to refuse edits adding parse errors; {@code null}: no validation
     */
    public ViewEditPlanner(CorpusView corpus, Function<TextId, Optional<TextDocument>> documents,
                           Function<TextDocument, List<GrammarPlugin>> grammars, EditValidator validator) {
        this.corpus = corpus;
        this.documents = documents;
        this.grammars = grammars;
        this.validator = validator;
    }

    /**
     * @param edits edits computed against the content the object was parsed from
     */
    public Prepared prepare(CorpusObject object, List<TextEdit> edits) {
        Optional<Text> text = corpus.textOf(object);
        if (text.isEmpty()) {
            return new Failed(new EditOutcome.Stale("The text changed: the object no longer exists"));
        }
        Optional<TextDocument> document = documents.apply(text.get().id());
        if (document.isEmpty()) {
            return new Failed(new EditOutcome.NotEditable("The file is no longer in the project"));
        }
        if (edits.isEmpty()) {
            return new Ready(document.get(), List.of());
        }
        String current = document.get().getCurrentText();
        List<TextEdit> located;
        if (current.equals(text.get().content())) {
            located = edits;
        } else {
            // typed since the last parse, or a parse in progress
            located = new ArrayList<>();
            for (TextEdit e : edits) {
                Optional<TextEdit> found = Relocation.find(e, current);
                if (found.isEmpty()) {
                    return new Failed(new EditOutcome.Stale("The text changed: parse it again and retry"));
                }
                located.add(found.get());
            }
        }
        if (!located.stream().allMatch(e -> e.matches(current))) {
            return new Failed(new EditOutcome.Stale("The text changed: parse it again and retry"));
        }
        if (validator != null) {
            EditValidator.Result result = validator.check(text.get().id(), current, located,
                grammars.apply(document.get()));
            if (!result.accepted()) {
                return new Failed(new EditOutcome.Invalid(result.newErrors()));
            }
        }
        return new Ready(document.get(), located);
    }
}
