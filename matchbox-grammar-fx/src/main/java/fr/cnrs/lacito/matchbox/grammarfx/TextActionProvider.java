package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.Text;
import java.util.List;

/**
 * Actions a plugin offers on a parsed text (e.g. "Add header" on a file without one).
 *
 * <p>Registered like {@link ObjectViewFactory}, in
 * {@code META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider}. The application
 * knows nothing of what the actions do: it shows them in the editor and applies their edits through
 * {@link ViewContext#runTextAction}. Without the plugin, nothing is shown.
 */
public interface TextActionProvider {

    /** Called on the JavaFX thread after each parse of the displayed text; empty if nothing to offer. */
    List<TextAction> actionsFor(Text text, ViewContext context);
}
