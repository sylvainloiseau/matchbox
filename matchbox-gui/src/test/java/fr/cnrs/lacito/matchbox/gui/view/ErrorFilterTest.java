package fr.cnrs.lacito.matchbox.gui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorFilterTest {

    private static final TextId T = new TextId(Path.of("t.md"));
    private static final List<GrammarPlugin> INSTALLED =
        List.of(new NotebookGrammarPlugin(), new MarkdownHeadingGrammarPlugin());

    private static Diagnostic error(String grammarId) {
        return Diagnostic.at(T, grammarId, 1, 0, "oops");
    }

    @Test
    void labelsShowTheDisplayNameAndTheId() {
        assertEquals("Notebook Grammar (notebook-grammar)", GrammarNames.label("notebook-grammar", INSTALLED));
        assertEquals("unknown-grammar", GrammarNames.label("unknown-grammar", INSTALLED));
        assertEquals("", GrammarNames.label(null, INSTALLED));
    }

    @Test
    void oneChoicePerPluginAfterAllPlugins() {
        List<Diagnostic> errors = List.of(error("notebook-grammar"), error("markdown-heading-grammar"),
            error("notebook-grammar"), error("gone-grammar"));
        List<ErrorFilter.Choice> choices = ErrorFilter.choices(errors, INSTALLED);
        assertEquals(List.of("All plugins (4)", "Notebook Grammar (notebook-grammar) — 2",
                "Markdown headings and metadata (markdown-heading-grammar) — 1", "gone-grammar — 1"),
            choices.stream().map(ErrorFilter.Choice::toString).toList());
        assertNull(choices.get(0).grammarId());
        assertTrue(choices.get(0).accepts(errors.get(1)));
        assertTrue(choices.get(1).accepts(errors.get(0)));
        assertFalse(choices.get(1).accepts(errors.get(1)));
    }

    @Test
    void theChosenPluginIsKeptWhileItHasErrors() {
        List<ErrorFilter.Choice> choices = ErrorFilter.choices(List.of(error("notebook-grammar")), INSTALLED);
        assertEquals("notebook-grammar", ErrorFilter.keep(choices, "notebook-grammar").grammarId());
        assertNull(ErrorFilter.keep(choices, "markdown-heading-grammar").grammarId(), "back to all plugins");
        assertNull(ErrorFilter.keep(ErrorFilter.choices(List.of(), INSTALLED), "notebook-grammar").grammarId());
    }

    @Test
    void tabTitles() {
        assertEquals("Parsing errors", ErrorFilter.tabTitle(0, 0, false));
        assertEquals("Parsing errors (12)", ErrorFilter.tabTitle(12, 12, false));
        assertEquals("Parsing errors (3/12)", ErrorFilter.tabTitle(3, 12, true));
    }

    @Test
    void tabTitlesCountWarningsApart() {
        Diagnostic warning = Diagnostic.warning(T, "g", 1, 0, "Not kept", 0, 3);
        Diagnostic error = error("g");
        assertEquals("Parsing errors (2)", ErrorFilter.tabTitle(2, List.of(error, error), false));
        assertEquals("Parsing errors (1 warning)", ErrorFilter.tabTitle(1, List.of(warning), false));
        assertEquals("Parsing errors (2 errors, 1 warning)", ErrorFilter.tabTitle(3, List.of(error, warning, error), false));
        assertEquals("Parsing errors (1/2)", ErrorFilter.tabTitle(1, List.of(error, warning), true));
        assertEquals("Warning", ErrorFilter.severityLabel(warning));
        assertEquals("Error", ErrorFilter.severityLabel(error));
    }
}
