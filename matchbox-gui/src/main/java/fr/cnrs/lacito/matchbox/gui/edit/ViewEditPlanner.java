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
import fr.cnrs.lacito.matchbox.corpus.EditRebase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prepares the edits a view asks for, without applying them: finds the object's text and
 * document, moves the edits to the current text of the document (which may have changed since
 * the object was parsed) and, if requested, refuses edits that add parse errors.
 *
 * <p>Edits are moved by history ({@link TextDocument#rebase}): through the changes made since the
 * version the text was parsed from, by position. An edit whose characters were changed since is
 * refused (Stale). Only when that history is not known is the text searched for the original of
 * each edit, accepted if it occurs once near its old place ({@link Relocation#find}).
 *
 * <p>No JavaFX controls: tested without a display.
 */
public final class ViewEditPlanner {

    /** Result of {@link #prepare}. */
    public sealed interface Prepared {}

    /**
     * Edits ready to be applied to the current text of {@code document}.
     *
     * @param requested the edits as asked for, relative to the parsed text
     * @param offsets   moves an offset of the parsed text (outside the edits) to the current text
     */
    public record Ready(TextDocument document, List<TextEdit> edits, List<TextEdit> requested,
                       IntUnaryOperator offsets) implements Prepared {
        public Ready(TextDocument document, List<TextEdit> edits) {
            this(document, edits, edits, IntUnaryOperator.identity());
        }

        /**
         * An offset of the text the requested edits would give from the parsed text (e.g. what a
         * text action selects), in the text the edits give from the current text.
         */
        public int moveResultOffset(int offset) {
            Integer[] order = new Integer[requested.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> requested.get(i).start()));
            int shift = 0;
            int before = 0;
            boolean found = false;
            for (int i : order) {
                if (requested.get(i).start() + before > offset) break;
                shift = edits.get(i).start() - requested.get(i).start();
                found = true;
                before += requested.get(i).delta();
            }
            return found ? offset + shift : offsets.applyAsInt(offset);
        }
    }

    /** Where a refused edit met a change made since the parse (1-based line of the current text). */
    public record Conflict(TextId text, int line) {}

    /** The edits cannot be applied; nothing was changed. */
    public record Failed(EditOutcome outcome, Optional<Conflict> conflict) implements Prepared {
        public Failed(EditOutcome outcome) {
            this(outcome, Optional.empty());
        }
    }

    private static final Logger logger = Logger.getLogger(ViewEditPlanner.class.getName());

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
        return prepare(text.get(), edits);
    }

    /**
     * Edits of a whole text (e.g. a text action), computed against the content of its last parse.
     */
    public Prepared prepare(TextId id, List<TextEdit> edits) {
        Optional<Text> text = corpus.text(id);
        if (text.isEmpty()) {
            return new Failed(new EditOutcome.Stale("The text is not parsed yet"));
        }
        return prepare(text.get(), edits);
    }

    private Prepared prepare(Text parsed, List<TextEdit> edits) {
        Optional<Text> text = Optional.of(parsed);
        Optional<TextDocument> document = documents.apply(text.get().id());
        if (document.isEmpty()) {
            return new Failed(new EditOutcome.NotEditable("The file is no longer in the project"));
        }
        if (edits.isEmpty()) {
            return new Ready(document.get(), List.of());
        }
        TextDocument doc = document.get();
        String current = doc.getCurrentText();
        List<TextEdit> located;
        IntUnaryOperator offsets = IntUnaryOperator.identity();
        long version = parsed.version();
        if (version != Text.UNKNOWN_VERSION && version == doc.getVersion()
            || version == Text.UNKNOWN_VERSION && current.equals(parsed.content())) {
            located = edits;
        } else {
            // typed since the last parse, or a parse in progress
            TextDocument.Rebased rebased = version == Text.UNKNOWN_VERSION ? new TextDocument.Rebased.Unknown()
                : doc.rebase(edits, version, parsed.content());
            switch (rebased) {
                case TextDocument.Rebased.Moved m -> {
                    located = m.edits();
                    long from = version;
                    long to = doc.getVersion();
                    offsets = o -> doc.moveOffset(o, EditRebase.Bias.BEFORE, from, to).orElse(o);
                }
                case TextDocument.Rebased.Conflict c -> {
                    return new Failed(new EditOutcome.Stale("You changed line " + c.line()
                        + " since it was parsed: parse it again and retry"), Optional.of(new Conflict(doc.getId(), c.line())));
                }
                case TextDocument.Rebased.Unknown u -> {
                    logger.info(() -> "No history of " + doc.getId() + " since the parse: edits searched in the text");
                    located = new ArrayList<>();
                    for (TextEdit e : edits) {
                        Optional<TextEdit> found = Relocation.find(e, current);
                        if (found.isEmpty()) {
                            return new Failed(new EditOutcome.Stale("The text changed: parse it again and retry"));
                        }
                        located.add(found.get());
                    }
                }
            }
        }
        if (!located.stream().allMatch(e -> e.matches(current))) {
            logger.log(Level.WARNING, "Edits moved to the current text of " + doc.getId() + " do not match it: " + located);
            return new Failed(new EditOutcome.Stale("The text changed: parse it again and retry"));
        }
        if (validator != null) {
            EditValidator.Result result = validator.check(text.get().id(), current, located,
                grammars.apply(document.get()));
            if (!result.accepted()) {
                return new Failed(new EditOutcome.Invalid(result.newErrors()));
            }
        }
        return new Ready(doc, located, edits, offsets);
    }
}
