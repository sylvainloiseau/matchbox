package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Filter of a problem table by plugin (the choice box above the Parsing errors and Semantic issues
 * tables), and the titles of their tabs. No JavaFX.
 */
public final class DiagnosticFilter {

    /** Title of the Parsing errors tab. */
    public static final String PARSING_ERRORS = "Parsing errors";
    /** Title of the Semantic issues tab. */
    public static final String SEMANTIC_ISSUES = "Semantic issues";

    /**
     * One choice of the filter.
     *
     * @param grammarId the plugin whose problems are shown; {@code null} for all plugins
     * @param label     text of the choice
     * @param count     number of problems it shows
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

    private DiagnosticFilter() {}

    /** "All plugins (n)", then one choice per plugin that reported problems, in order of appearance. */
    public static List<Choice> choices(List<Diagnostic> problems, List<GrammarPlugin> installed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Diagnostic d : problems) {
            counts.merge(Objects.toString(d.grammarId(), ""), 1, Integer::sum);
        }
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(null, "All plugins (" + problems.size() + ")", problems.size()));
        counts.forEach((id, n) -> choices.add(new Choice(id, GrammarNames.label(id, installed) + " — " + n, n)));
        return choices;
    }

    /** The choice of the previously selected plugin if it still has problems, otherwise "All plugins". */
    public static Choice keep(List<Choice> choices, String previousGrammarId) {
        return choices.stream()
            .filter(c -> previousGrammarId != null && previousGrammarId.equals(c.grammarId()))
            .findFirst()
            .orElse(choices.get(0));
    }

    /** "Parsing errors", "Parsing errors (12)", or "Parsing errors (3/12)" when filtered. */
    public static String tabTitle(int shown, int total, boolean filtered) {
        return tabTitle(PARSING_ERRORS, shown, total, filtered);
    }

    /** "Title", "Title (12)", or "Title (3/12)" when filtered. */
    public static String tabTitle(String title, int shown, int total, boolean filtered) {
        if (total == 0) return title;
        return filtered ? title + " (" + shown + "/" + total + ")" : title + " (" + total + ")";
    }

    /** {@link #tabTitle(String, int, List, boolean, int)} of the Parsing errors tab, for one file. */
    public static String tabTitle(int shown, List<Diagnostic> all, boolean filtered) {
        return tabTitle(PARSING_ERRORS, shown, all, filtered, 0);
    }

    /**
     * The title of a problem tab. With warnings, both are counted: "Title (2 errors, 1 warning)",
     * "Title (1 warning)"; "Title (1/3)" when filtered; with {@code files > 0} (the table lists the
     * whole project), " in n files" is added: "Title (4 in 3 files)".
     *
     * @param files number of files of {@code all}; 0 when the table shows one file
     */
    public static String tabTitle(String title, int shown, List<Diagnostic> all, boolean filtered, int files) {
        if (all.isEmpty()) return title;
        long warnings = all.stream().filter(Diagnostic::isWarning).count();
        long errors = all.size() - warnings;
        String counts;
        if (filtered) {
            counts = shown + "/" + all.size();
        } else if (warnings == 0) {
            counts = String.valueOf(all.size());
        } else {
            counts = (errors == 0 ? "" : plural(errors, "error") + ", ") + plural(warnings, "warning");
        }
        String where = files > 0 ? " in " + plural(files, "file") : "";
        return title + " (" + counts + where + ")";
    }

    private static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    /** "Error" or "Warning", the text of the severity column. */
    public static String severityLabel(Diagnostic d) {
        return d.isWarning() ? "Warning" : "Error";
    }
}
