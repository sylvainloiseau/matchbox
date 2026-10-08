package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;

/** Texts parsed with the notebook grammar, for the tests. */
final class ParsedTexts {

    static final TextId ID = TextId.of(Path.of("notes.txt"));
    private static final GrammarPlugin GRAMMAR = new NotebookGrammarPlugin();

    private ParsedTexts() {
    }

    static Text parse(TextId id, String content) {
        ParseResult r = GRAMMAR.getResult(id, content);
        return Text.builder(id, content).add(GRAMMAR.getId(), r.objects(), r.diagnostics()).build();
    }

    static Text parse(String content) {
        return parse(ID, content);
    }
}
