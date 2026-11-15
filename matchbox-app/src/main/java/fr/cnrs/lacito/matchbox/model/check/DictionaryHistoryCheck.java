package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryRecorder;
import java.util.List;

/**
 * "Dictionary history": the problems found by applying the mutation commands of the project to
 * its dictionary (the first dynamic error, warnings of the engine, a dictionary that cannot be
 * read), as semantic findings of the texts they are in. They come from the last history computed
 * ({@link CheckInputs#history()}).
 */
public final class DictionaryHistoryCheck implements CorpusCheck {

    @Override
    public String id() {
        return HistoryRecorder.CHECK_ID;
    }

    @Override
    public List<Diagnostic> check(Text text, CheckInputs inputs) {
        return inputs.history().map(h -> h.diagnosticsOf(text.id()).stream()
                .filter(d -> d.startIndex() < 0 || d.stopIndex() < text.content().length())
                .toList())
            .orElse(List.of());
    }
}
