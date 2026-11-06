package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseContext;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.util.List;

/**
 * A second reading of the IGTs of the notebook notation, as another plugin with its own notation
 * could produce: the same spans, its own kind. Records the context it received.
 */
final class DuplicateIgtGrammar implements GrammarPlugin {

    static final String ID = "duplicate-igt";
    private final GrammarPlugin notebook = new NotebookGrammarPlugin();
    volatile ParseContext received;

    @Override public String getId() { return ID; }
    @Override public String getDisplayName() { return "Duplicate IGT reader"; }
    @Override public String getDisplayDescription() { return "Reads the IGTs of the notebook notation again"; }
    @Override public String getFormatDocumentation() { return ""; }
    @Override public List<GrammarExample> getExamples() { return List.of(); }
    @Override public String getGrammarSource() { return ""; }
    @Override public int getApiVersion() { return API_VERSION; }

    @Override
    public ParseResult getResult(TextId text, String content) {
        List<CorpusObject> igts = notebook.getResult(text, content).objects().stream()
            .filter(InterlinearSentence.class::isInstance)
            .map(o -> (CorpusObject) new SimpleIGT(ID + ":igt", o.getTextCoordinate(), "t", "m", "f"))
            .toList();
        return new ParseResult(igts, List.of());
    }

    @Override
    public ParseResult getResult(TextId text, String content, ParseContext context) {
        received = context;
        return getResult(text, content);
    }
}
