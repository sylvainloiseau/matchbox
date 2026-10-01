package fr.cnrs.lacito.matchbox.grammarapi;

import java.nio.file.Path;
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
    int API_VERSION = 2;

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
     * Parses one file.
     *
     * <p>Must be re-entrant and thread-safe: it is called outside the JavaFX thread, possibly
     * concurrently for several files. Must return new lists, never {@code null}, and report
     * failures as {@link ParseError}s rather than printing them.
     */
    ParseResult getResult(Path filePath, String content);

    /**
     * Version of the contract the plugin was compiled against. Implement it as
     * {@code return API_VERSION;} in the plugin class itself: the constant is then compiled into
     * the plugin, which is what makes the check meaningful (a default method here would always
     * return the application's version).
     */
    int getApiVersion();
}
