package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses a text with the grammars of its file, in rank order: each grammar receives the claims of
 * the earlier ones ({@link ParseContext}), and {@link Text.Builder} keeps one reading of each
 * character (see {@link fr.cnrs.lacito.matchbox.corpus.MatchRole}). Used by the project and by the
 * validation of proposed edits, so that both apply the same rule.
 *
 * <p>Thread-safe; call it off the JavaFX thread.
 */
public final class TextParser {

    private static final Logger logger = Logger.getLogger(TextParser.class.getName());

    private TextParser() {}

    /**
     * Runs the grammars in order on {@code content}. A grammar failing (exception, {@code null}
     * result) contributes an internal-error diagnostic and no object.
     */
    public static Text parse(TextId id, String content, List<? extends GrammarPlugin> grammars) {
        Text.Builder builder = Text.builder(id, content);
        for (GrammarPlugin plugin : grammars) {
            ParseResult result;
            try {
                ParseContext context = new ParseContext(builder.claims());
                result = plugin.getResult(id, content, context);
                if (result == null) {
                    throw new IllegalStateException("getResult returned null");
                }
            } catch (RuntimeException | LinkageError e) {
                logger.log(Level.WARNING, "Grammar " + plugin.getId() + " failed on " + id, e);
                result = new ParseResult(List.of(), List.of(
                    Diagnostic.at(id, plugin.getId(), 0, 0, "Internal error of the grammar: " + e)
                ));
            }
            builder.add(plugin.getId(), displayName(plugin), result.objects(), result.diagnostics());
        }
        return builder.build();
    }

    /** The plugin's display name, its id if it has none or fails. */
    static String displayName(GrammarPlugin plugin) {
        try {
            String name = plugin.getDisplayName();
            return name == null || name.isBlank() ? plugin.getId() : name;
        } catch (RuntimeException | LinkageError e) {
            return plugin.getId();
        }
    }
}
