package fr.cnrs.lacito.matchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginManagerTest {

    /** Declared in src/test/resources/META-INF/services, i.e. on the application class path. */
    public static class ClassPathGrammar implements GrammarPlugin {
        public String getId() { return "classpath-grammar"; }
        public String getDisplayName() { return "Class path grammar"; }
        public String getDisplayDescription() { return ""; }
        public ParseResult getResult(Path file, String content) { return ParseResult.empty(); }
        public int getApiVersion() { return API_VERSION; }
    }

    /**
     * jpackage puts the plugin jars on the application class path: their grammars must be
     * loaded (they were skipped, and the installed application failed at startup).
     */
    @Test
    void loadsGrammarsOfTheApplicationClassPath() throws Exception {
        List<GrammarPlugin> plugins = PluginManager.loadPlugins();
        assertEquals(1, plugins.stream().filter(p -> p.getId().equals("classpath-grammar")).count());
        assertEquals("classpath-grammar", PluginManager.getPluginById("classpath-grammar").getId());
    }
}
