package fr.cnrs.lacito.matchbox.grammar.notebook;

import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.Igt_paragraphContext;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.SingleLineEntryContext;
import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR listener for extracting three-line groups
 */
public class NotebookListener extends NotebookParserBaseListener {

    private final List<Igt_paragraphContext> iGTs;
    private final List<SingleLineEntryContext> lexs;

    public NotebookListener(String content) {
        this.iGTs = new ArrayList<>();
        this.lexs = new ArrayList<>();
    }

    @Override
    public void enterIgt_paragraph(NotebookParser.Igt_paragraphContext ctx) {
        try {
            iGTs.add(ctx);
        } catch (Exception e) {
            System.err.println("Error parsing three-line group: " + e.getMessage());
        }
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

    public List<Igt_paragraphContext> getIGTs() {
        return iGTs;
    }

    public List<SingleLineEntryContext> getLexicalDefinitions() {
        return lexs;
    }

}
