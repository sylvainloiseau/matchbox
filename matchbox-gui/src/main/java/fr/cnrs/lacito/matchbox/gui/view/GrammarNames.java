package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.util.List;

/** How a grammar (plugin) is named in the GUI: its display name with its id. */
public final class GrammarNames {

    private GrammarNames() {}

    /**
     * {@code "Notebook Grammar (notebook-grammar)"}; the id alone if no installed grammar has it;
     * an empty string for {@code null}.
     */
    public static String label(String grammarId, List<GrammarPlugin> installed) {
        if (grammarId == null) return "";
        return installed.stream()
            .filter(g -> grammarId.equals(g.getId()))
            .findFirst()
            .map(g -> g.getDisplayName() + " (" + grammarId + ")")
            .orElse(grammarId);
    }
}
