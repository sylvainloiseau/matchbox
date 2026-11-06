package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.List;

/**
 * Contract of a grammar plugin, discovered with {@link java.util.ServiceLoader} in the jars of the
 * plugins directory.
 */
public interface GrammarPlugin {

    /**
     * Version of this contract. Plugins returning another value from {@link #getApiVersion()}
     * are not loaded.
     */
    int API_VERSION = 3;

    /**
     * Stable identifier. It is stored in project (.mbx) files and prefixes the kind of every
     * object the grammar produces ({@code "<id>:<name>"}), so it must not change.
     */
    String getId();

    String getDisplayName();

    String getDisplayDescription();

    /**
     * Description of the notation recognised by the grammar (Markdown allowed), detailed enough
     * to be given to an AI assistant: what each construct means linguistically, not only its
     * syntax. Empty string if not documented yet; never {@code null}.
     */
    String getFormatDocumentation();

    /**
     * Canonical snippets of the notation, each parsing without errors with this grammar alone.
     * Empty list if none; never {@code null}.
     */
    List<GrammarExample> getExamples();

    /**
     * Source of the grammar (e.g. the ANTLR lexer and parser {@code .g4} files, concatenated).
     * Empty string if not provided; never {@code null}.
     */
    String getGrammarSource();

    /**
     * Parses one text.
     *
     * <p>Must be re-entrant and thread-safe: it is called outside the JavaFX thread, possibly
     * concurrently for several texts. Must return new lists, never {@code null}, and report
     * failures as {@link fr.cnrs.lacito.matchbox.corpus.Diagnostic}s rather than printing them.
     * Every span of the result refers to {@code text} (use {@link Spans#of}).
     *
     * @param text    id of the text, to put in the spans and diagnostics
     * @param content the text to parse
     */
    ParseResult getResult(TextId text, String content);

    /**
     * Parses one text knowing what the grammars ranked before this one for the file already read
     * (see {@link ParseContext}). The application calls this method; the default ignores the
     * context and calls {@link #getResult(TextId, String)}. Override it to skip text claimed by
     * another grammar, or not to report errors there. Same contract as
     * {@link #getResult(TextId, String)}.
     */
    default ParseResult getResult(TextId text, String content, ParseContext context) {
        return getResult(text, content);
    }

    /**
     * Writes changes of values into the text, in the notation of this grammar (used by editable
     * views). The default replaces the raw text of a field verbatim; override it when the
     * notation needs escaping.
     */
    default ObjectEditor getObjectEditor() {
        return ObjectEditor.VERBATIM;
    }

    /**
     * The kinds of the objects the grammar may produce ({@code "<id>:<name>"}), so that the user
     * can give each one its own display style before any file contains it. Optional: the
     * application also lists the kinds found in the corpus.
     */
    default List<String> getObjectKinds() {
        return List.of();
    }

    /**
     * Default display style of the objects of a kind, or of all the objects of the grammar when
     * {@code kind} is null. The styles chosen in the project come first; the properties they do
     * not set are taken from these defaults (kind first, then grammar).
     */
    default DisplayStyle getDefaultDisplayStyle(String kind) {
        return DisplayStyle.EMPTY;
    }

    /**
     * Version of the contract the plugin was compiled against. Implement it as
     * {@code return API_VERSION;} in the plugin class itself: the constant is then compiled into
     * the plugin, which is what makes the check meaningful (a default method here would always
     * return the application's version).
     */
    int getApiVersion();
}
