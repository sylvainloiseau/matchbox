package fr.cnrs.lacito.matchbox.assist.suggestion;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Readable names of the tokens of a grammar, from the optional resource
 * {@code assist/token-names.properties} next to the plugin class (e.g. {@code IMB_LINE=gloss line}).
 */
public final class TokenNames {

    static final String RESOURCE = "assist/token-names.properties";

    private static final Map<Class<?>, Properties> CACHE = new ConcurrentHashMap<>();

    private TokenNames() {}

    /** The readable name of a token as given by {@code ParseError.getExpected()}, or the token itself. */
    public static String readable(GrammarPlugin grammar, String token) {
        if (grammar == null) {
            return token;
        }
        String name = load(grammar.getClass()).getProperty(token);
        return name == null || name.isBlank() ? token : name.strip();
    }

    private static Properties load(Class<?> pluginClass) {
        return CACHE.computeIfAbsent(pluginClass, c -> {
            Properties p = new Properties();
            // resolved through the plugin class loader
            try (InputStream in = c.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // no names
            }
            return p;
        });
    }
}
