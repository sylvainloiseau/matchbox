package fr.cnrs.lacito.matchbox.grammar.liftpatchbox;

import fr.cnrs.lacito.liftpatchbox.language.Syntax;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.util.List;

/**
 * What the two LiftPatch plugins share: reading a text with lift-patchbox, the kinds per verb
 * family and their default styles. A plugin gives its id, its syntax and its documentation.
 */
public abstract class AbstractLiftPatchGrammarPlugin extends AbstractGrammarPlugin {

    private final LiftPatchReader reader;

    protected AbstractLiftPatchGrammarPlugin(Syntax syntax) {
        this.reader = new LiftPatchReader(getId(), syntax);
    }

    @Override
    public ParseResult getResult(TextId text, String content) {
        try {
            return reader.read(text, content);
        } catch (RuntimeException | LinkageError e) {
            return new ParseResult(List.of(),
                List.of(Diagnostic.internal(text, getId(), 0, "Internal error of the grammar: " + e)));
        }
    }

    @Override
    public List<String> getObjectKinds() {
        return LiftPatchKinds.all(getId());
    }

    @Override
    public DisplayStyle getDefaultDisplayStyle(String kind) {
        return LiftPatchKinds.defaultStyle(getId(), kind);
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public String getGrammarSource() {
        return "";
    }
}
