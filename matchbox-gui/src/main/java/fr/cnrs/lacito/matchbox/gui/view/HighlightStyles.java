package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.MatchRole;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.workbench.ProjectSession;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javafx.beans.value.ChangeListener;

/**
 * Display styles of corpus objects in the editor and the views. The style of an object is, property
 * by property: the project's style of its kind, the project's style of its grammar, the grammar's
 * default for the kind, the grammar's default, then a palette background by kind (none for a
 * {@code Sense} or a {@linkplain MatchRole#SCOPE scope}).
 */
public class HighlightStyles {

    /** Light background colours for kinds without a background of their own. */
    private static final List<String> PALETTE = List.of(
        "#fde2e4", "#dfe7fd", "#fff1c1", "#e2ece9",
        "#e8dff5", "#d4f0f0", "#fce1c8", "#e0f2d8"
    );

    private final ProjectSession session;
    private final Function<String, GrammarPlugin> plugins;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ChangeListener<DisplayStyles> stylesListener = (obs, o, n) -> fireChanged();

    /**
     * @param plugins the installed grammar of an id, null if it is not installed
     */
    public HighlightStyles(ProjectSession session, Function<String, GrammarPlugin> plugins) {
        this.session = session;
        this.plugins = plugins;
        session.projectProperty().addListener((obs, o, n) -> {
            if (o != null) o.displayStylesProperty().removeListener(stylesListener);
            if (n != null) n.displayStylesProperty().addListener(stylesListener);
            fireChanged();
        });
        if (session.project() != null) session.project().displayStylesProperty().addListener(stylesListener);
    }

    /** Called when the styles may have changed (project changed, styles edited). */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }

    /** The style of an object of {@code text} (its grammar is found in the text). */
    public DisplayStyle styleFor(Text text, CorpusObject object) {
        String grammar = text == null ? null : text.grammarIdOf(object).orElse(null);
        return resolve(grammar == null ? grammarOfKind(object.getKind()) : grammar, object);
    }

    /** The style of an object of the current corpus. */
    public DisplayStyle styleFor(CorpusObject object) {
        return styleFor(session.corpus().textOf(object).orElse(null), object);
    }

    /**
     * Background colour of an object, or {@code null} if it has no highlight of its own (it then
     * shows its parent's colour).
     */
    public String colorFor(CorpusObject object) {
        return styleFor(object).backgroundColor();
    }

    private DisplayStyle resolve(String grammarId, CorpusObject object) {
        String kind = object.getKind();
        // a sense shows the colour of its entry; a scope (section) gets no colour of its own: it
        // would tint pages of prose and the objects it contains
        DisplayStyle fallback = object instanceof Sense || object.getMatchRole() == MatchRole.SCOPE
            ? DisplayStyle.EMPTY : DisplayStyle.background(paletteColor(kind));
        if (grammarId == null) return fallback;
        Project project = session.project();
        DisplayStyle style = project == null ? DisplayStyle.EMPTY : project.getDisplayStyles().resolve(grammarId, kind);
        return style.over(pluginDefault(plugins.apply(grammarId), kind)).over(fallback);
    }

    /**
     * What an object of {@code kind} looks like when the project sets nothing: the plugin's default
     * for the kind, the plugin's default, then the palette background (used by previews).
     */
    public static DisplayStyle defaultStyle(GrammarPlugin plugin, String kind) {
        return pluginDefault(plugin, kind).over(DisplayStyle.background(paletteColor(kind == null ? "" : kind)));
    }

    private static DisplayStyle pluginDefault(GrammarPlugin plugin, String kind) {
        if (plugin == null) return DisplayStyle.EMPTY;
        return safeDefault(plugin, kind).over(safeDefault(plugin, null));
    }

    /** A plugin's default style, empty if the plugin fails. */
    private static DisplayStyle safeDefault(GrammarPlugin plugin, String kind) {
        try {
            DisplayStyle s = plugin.getDefaultDisplayStyle(kind);
            return s == null ? DisplayStyle.EMPTY : s.validated();
        } catch (RuntimeException | LinkageError e) {
            return DisplayStyle.EMPTY;
        }
    }

    /** {@code "tag-grammar"} for {@code "tag-grammar:tag"}; null without prefix. */
    private static String grammarOfKind(String kind) {
        int colon = kind == null ? -1 : kind.indexOf(':');
        return colon > 0 ? kind.substring(0, colon) : null;
    }

    static String paletteColor(String kind) {
        return PALETTE.get(Math.floorMod(kind.hashCode(), PALETTE.size()));
    }
}
