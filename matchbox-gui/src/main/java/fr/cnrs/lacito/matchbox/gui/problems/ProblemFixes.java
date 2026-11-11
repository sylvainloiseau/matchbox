package fr.cnrs.lacito.matchbox.gui.problems;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.util.ArrayList;
import java.util.List;

/**
 * The fixes of the diagnostics of a text as {@link TextAction}s, shown in the ✚ menu of their
 * line with the actions of the plugins and applied the same way. Pure.
 */
public final class ProblemFixes {

    private ProblemFixes() {}

    public static List<TextAction> actions(Text text) {
        return actions(text, text.diagnostics());
    }

    /** The fixes of the given diagnostics of a text (parse diagnostics and findings of the checks). */
    public static List<TextAction> actions(Text text, List<Diagnostic> diagnostics) {
        List<TextAction> actions = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            if (d.fixes().isEmpty()) continue;
            int offset = d.startIndex() >= 0 ? Math.min(d.startIndex(), text.content().length())
                : lineStart(text.content(), d.line());
            for (Diagnostic.Fix fix : d.fixes()) {
                actions.add(new TextAction("Fix: " + fix.label(), d.message(), offset, fix.edits(), -1));
            }
        }
        return actions;
    }

    static int lineStart(String content, int line) {
        int offset = 0;
        for (int l = 1; l < line; l++) {
            int nl = content.indexOf('\n', offset);
            if (nl < 0) return offset;
            offset = nl + 1;
        }
        return offset;
    }
}
