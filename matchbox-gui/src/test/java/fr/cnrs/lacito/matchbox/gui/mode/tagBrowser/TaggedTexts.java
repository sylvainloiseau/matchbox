package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.tag.TagGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;

/** Texts parsed with the notebook and tag grammars, for the tests. */
final class TaggedTexts {

    static final List<GrammarPlugin> GRAMMARS = List.of(new NotebookGrammarPlugin(), new TagGrammarPlugin());

    private TaggedTexts() {
    }

    static TextId id(String name) {
        return TextId.of(Path.of(name));
    }

    static Text parse(TextId id, String content) {
        Text.Builder builder = Text.builder(id, content);
        for (GrammarPlugin g : GRAMMARS) {
            ParseResult r = g.getResult(id, content);
            builder.add(g.getId(), r.objects(), r.diagnostics());
        }
        return builder.build();
    }

    /** A corpus of texts given as name, content, name, content… */
    static Corpus corpus(String... namesAndContents) {
        Corpus corpus = new Corpus();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            corpus.put(parse(id(namesAndContents[i]), namesAndContents[i + 1]));
        }
        return corpus;
    }
}
