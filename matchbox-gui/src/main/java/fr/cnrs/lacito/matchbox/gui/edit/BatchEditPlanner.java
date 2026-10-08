package fr.cnrs.lacito.matchbox.gui.edit;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Prepares a batch of field changes (e.g. a tag renamed in several files), all or nothing, without
 * applying it: every edit is computed by the object's grammar against the same parse of its text,
 * the edits of a text must not overlap, and each text's edits are checked as one change by the
 * {@link ViewEditPlanner} (current text, writable file, no new parse errors). If anything fails,
 * the plan lists every problem and nothing is to be applied.
 *
 * <p>No JavaFX controls: tested without a display.
 */
public final class BatchEditPlanner {

    /** Result of {@link #plan}. */
    public sealed interface Plan {}

    /** One {@link ViewEditPlanner.Ready} per document, in the order of the changes. */
    public record Ready(List<ViewEditPlanner.Ready> documents) implements Plan {}

    /** Nothing can be applied; the reasons, one per line, with the file they concern. */
    public record Failed(List<String> problems) implements Plan {}

    private final CorpusView corpus;
    private final ViewEditPlanner planner;
    private final BiFunction<Text, CorpusObject, Optional<GrammarPlugin>> grammarOf;
    private final Predicate<TextDocument> writable;

    /**
     * @param grammarOf the grammar that produced an object of a text, if it still applies to its file
     * @param writable  whether a document's file can be written
     */
    public BatchEditPlanner(CorpusView corpus, ViewEditPlanner planner,
                            BiFunction<Text, CorpusObject, Optional<GrammarPlugin>> grammarOf,
                            Predicate<TextDocument> writable) {
        this.corpus = corpus;
        this.planner = planner;
        this.grammarOf = grammarOf;
        this.writable = writable;
    }

    public Plan plan(List<FieldChange> changes) {
        Set<String> problems = new LinkedHashSet<>();
        Map<TextId, List<TextEdit>> editsByText = new LinkedHashMap<>();
        for (FieldChange change : changes) {
            Optional<Text> text = corpus.textOf(change.object());
            if (text.isEmpty()) {
                problems.add(change.object().getLabel() + ": the text changed, the object no longer exists");
                continue;
            }
            String where = name(text.get().id());
            Optional<GrammarPlugin> grammar = grammarOf.apply(text.get(), change.object());
            if (grammar.isEmpty()) {
                problems.add(where + ": the grammar of “" + change.object().getLabel() + "” no longer applies to the file");
                continue;
            }
            EditProposal proposal;
            try {
                proposal = grammar.get().getObjectEditor().setField(change.object(), change.field(), change.value(),
                    text.get().content());
            } catch (RuntimeException | LinkageError e) {
                problems.add(where + ": the grammar " + grammar.get().getId() + " failed to write the value: " + e);
                continue;
            }
            switch (proposal) {
                case EditProposal.Refused r -> problems.add(where + ": " + r.reason());
                case EditProposal.Edits e -> editsByText.computeIfAbsent(text.get().id(), k -> new ArrayList<>())
                    .addAll(e.edits());
            }
        }
        List<ViewEditPlanner.Ready> ready = new ArrayList<>();
        editsByText.forEach((id, edits) -> {
            String where = name(id);
            if (overlap(edits)) {
                problems.add(where + ": two changes concern the same place of the text");
                return;
            }
            switch (planner.prepare(id, edits)) {
                case ViewEditPlanner.Failed f -> problems.add(where + ": " + f.outcome().message());
                case ViewEditPlanner.Ready r -> {
                    if (!r.edits().isEmpty() && !writable.test(r.document())) {
                        problems.add(where + ": the file cannot be written");
                    } else {
                        ready.add(r);
                    }
                }
            }
        });
        return problems.isEmpty() ? new Ready(List.copyOf(ready)) : new Failed(List.copyOf(problems));
    }

    static boolean overlap(List<TextEdit> edits) {
        List<TextEdit> sorted = edits.stream().sorted(Comparator.comparingInt(TextEdit::start)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).start() < sorted.get(i - 1).end()) return true;
        }
        return false;
    }

    private static String name(TextId id) {
        return id.displayName();
    }
}
