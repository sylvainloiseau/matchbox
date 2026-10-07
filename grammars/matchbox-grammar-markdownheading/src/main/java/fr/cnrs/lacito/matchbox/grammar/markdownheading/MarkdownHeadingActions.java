package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.List;

/**
 * "Add header" on a file parsed by this grammar that has no header: inserts two {@code ---} lines
 * at the start; the empty header is then selected and its view lets the user add entries.
 */
public class MarkdownHeadingActions implements TextActionProvider {

    @Override
    public List<TextAction> actionsFor(Text text, ViewContext context) {
        if (!text.grammarIds().contains(MarkdownHeadingGrammarPlugin.ID)
            || text.objects(MarkdownHeadingGrammarPlugin.ID).stream().anyMatch(TextHeader.class::isInstance)) {
            return List.of();
        }
        String eol = LineIndex.lineEnding(text.content());
        return List.of(new TextAction("Add header", "Insert a metadata header (two --- lines) at the start of the file",
            0, List.of(TextEdit.insert(0, "---" + eol + "---" + eol)), 0));
    }
}
