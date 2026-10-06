package fr.cnrs.lacito.matchbox.assist.prompt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses examples from the parsed texts of a project: the first interlinear sentences and lexical
 * entries (by text id, then offset) whose span contains no parse error. The choice is
 * deterministic, so that the prompt prefix, and the provider's cache, stay stable.
 */
public final class FewShotSelector {

    public static final int PER_KIND = 3;
    static final int MAX_LENGTH = 800;

    private FewShotSelector() {}

    public static List<FewShotExample> select(List<Text> texts) {
        List<Text> sorted = texts.stream().sorted(Comparator.comparing(Text::id)).toList();
        List<FewShotExample> result = new ArrayList<>();
        result.addAll(pick(sorted, InterlinearSentence.class, "interlinear sentence"));
        result.addAll(pick(sorted, LexicalEntry.class, "lexical entry"));
        return result;
    }

    private static List<FewShotExample> pick(List<Text> texts, Class<?> type, String kind) {
        List<FewShotExample> picked = new ArrayList<>();
        for (Text t : texts) {
            List<CorpusObject> candidates = t.flatten()
                .filter(type::isInstance)
                .sorted(Comparator.comparingInt(o -> o.getTextCoordinate().startIndex()))
                .toList();
            for (CorpusObject o : candidates) {
                if (picked.size() == PER_KIND) {
                    return picked;
                }
                TextCoordinate span = o.getTextCoordinate();
                if (!span.isValidFor(t.content().length()) || hasError(span, t.diagnostics())) {
                    continue;
                }
                String text = t.content().substring(span.startIndex(), span.stopIndex() + 1).strip();
                if (!text.isEmpty() && text.length() <= MAX_LENGTH) {
                    picked.add(new FewShotExample(kind, text));
                }
            }
        }
        return picked;
    }

    private static boolean hasError(TextCoordinate span, List<Diagnostic> errors) {
        for (Diagnostic e : errors) {
            if (e.startIndex() >= 0 ? span.contains(e.startIndex())
                    : e.line() >= span.startLine() && e.line() <= span.endLine()) {
                return true;
            }
        }
        return false;
    }
}
