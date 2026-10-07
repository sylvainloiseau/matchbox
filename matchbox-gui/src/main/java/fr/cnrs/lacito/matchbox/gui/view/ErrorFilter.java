package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Filter of the parse errors by plugin (the choice box above the error table). No JavaFX. */
public final class ErrorFilter {

    /**
     * One choice of the filter.
     *
     * @param grammarId the plugin whose errors are shown; {@code null} for all plugins
     * @param label     text of the choice
     * @param count     number of errors it shows
     */
    public record Choice(String grammarId, String label, int count) {

        public boolean accepts(Diagnostic d) {
            return grammarId == null || grammarId.equals(d.grammarId());
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private ErrorFilter() {}

    /** "All plugins (n)", then one choice per plugin that reported errors, in order of appearance. */
    public static List<Choice> choices(List<Diagnostic> errors, List<GrammarPlugin> installed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Diagnostic d : errors) {
            counts.merge(Objects.toString(d.grammarId(), ""), 1, Integer::sum);
        }
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(null, "All plugins (" + errors.size() + ")", errors.size()));
        counts.forEach((id, n) -> choices.add(new Choice(id, GrammarNames.label(id, installed) + " — " + n, n)));
        return choices;
    }

    /** The choice of the previously selected plugin if it still has errors, otherwise "All plugins". */
    public static Choice keep(List<Choice> choices, String previousGrammarId) {
        return choices.stream()
            .filter(c -> previousGrammarId != null && previousGrammarId.equals(c.grammarId()))
            .findFirst()
            .orElse(choices.get(0));
    }

    /** "Parsing errors", "Parsing errors (12)", or "Parsing errors (3/12)" when filtered. */
    public static String tabTitle(int shown, int total, boolean filtered) {
        if (total == 0) return "Parsing errors";
        return filtered ? "Parsing errors (" + shown + "/" + total + ")" : "Parsing errors (" + total + ")";
    }
}
