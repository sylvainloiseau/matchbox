package fr.cnrs.lacito.matchbox.grammar.notebook;

import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.SingleLineEntryContext;
import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR listener collecting the lexical entries
 */
public class NotebookListener extends NotebookParserBaseListener {

    private final List<SingleLineEntryContext> lexs;

    public NotebookListener(String content) {
        this.lexs = new ArrayList<>();
    }

    // @Override
    // public void enterLexSimpleEntry(NotebookParser.LexSimpleEntryContext ctx) {
    //     try {
    //         lexs.add(ctx);
    //     } catch (Exception e) {
    //         System.err.println("Error parsing single-line entry: " + e.getMessage());
    //     }
    // }

    @Override
    public void enterSingleLineEntry(NotebookParser.SingleLineEntryContext ctx) {
        try {
            lexs.add(ctx);
        } catch (Exception e) {
            System.err.println("Error parsing single-line entry: " + e.getMessage());
        }
    }

    public List<SingleLineEntryContext> getLexicalDefinitions() {
        return lexs;
    }

}
