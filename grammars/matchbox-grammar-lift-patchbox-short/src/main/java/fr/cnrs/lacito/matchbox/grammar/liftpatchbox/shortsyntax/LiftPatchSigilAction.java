package fr.cnrs.lacito.matchbox.grammar.liftpatchbox.shortsyntax;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * "Declare LiftPatch sigil" on a file read by the concise syntax that has a syntax error and no
 * sigil: in notes, a line of prose that looks like a command is read as one. The action writes
 * {@code %liftpatch 1.1 sigil=">"} on the first line and a {@code >} before each command that
 * parsed; the lines that did not parse become prose.
 */
public class LiftPatchSigilAction implements TextActionProvider {

    static final String SIGIL = ">";

    @Override
    public List<TextAction> actionsFor(Text text, ViewContext context) {
        String id = LiftPatchShortGrammarPlugin.ID;
        if (!text.grammarIds().contains(id) || text.content().strip().startsWith("%liftpatch")) {
            return List.of();
        }
        boolean syntaxError = text.diagnostics(id).stream()
            .anyMatch(d -> d.category() == Diagnostic.Category.SYNTAX && d.severity() == Diagnostic.Severity.ERROR);
        if (!syntaxError) {
            return List.of();
        }
        String content = text.content();
        TreeSet<Integer> lineStarts = new TreeSet<>();
        collect(text.objects(id), content, lineStarts);
        String eol = content.contains("\r\n") ? "\r\n" : "\n";
        List<TextEdit> edits = new ArrayList<>();
        edits.add(TextEdit.insert(0, "%liftpatch 1.1 sigil=\"" + SIGIL + "\"" + eol));
        for (int start : lineStarts) {
            edits.add(TextEdit.insert(start, SIGIL));
        }
        return List.of(new TextAction("Declare LiftPatch sigil",
            "Only lines starting with " + SIGIL + " are commands: prose that looks like a command is left alone",
            0, edits, -1));
    }

    /** The start of each line holding a command, a block's inner commands included. */
    private static void collect(List<? extends CorpusObject> objects, String content, TreeSet<Integer> out) {
        for (CorpusObject o : objects) {
            if (o instanceof LexiconMutationCommand c) {
                int start = c.getTextCoordinate().startIndex();
                int lineStart = content.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
                if (start == 0) lineStart = 0;
                int cr = content.lastIndexOf('\r', Math.max(0, start - 1)) + 1;
                out.add(Math.max(lineStart, Math.min(cr, start)));
                collect(c.getChildren(), content, out);
            }
        }
    }
}
