package fr.cnrs.lacito.matchbox.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every kind a built-in grammar produces on its own examples is declared by {@code getObjectKinds()}. */
class DeclaredKindsTest {

    private static Stream<CorpusObject> withChildren(CorpusObject o) {
        return Stream.concat(Stream.of(o), o.getChildren().stream().flatMap(DeclaredKindsTest::withChildren));
    }

    @Test
    void theKindsOfTheExamplesAreDeclared() throws Exception {
        int checked = 0;
        for (GrammarPlugin plugin : PluginManager.loadPlugins()) {
            if (plugin.getObjectKinds().isEmpty()) continue;
            for (GrammarExample example : plugin.getExamples()) {
                for (CorpusObject o : plugin.getResult(TextId.of(Path.of("example.txt")), example.text()).objects()) {
                    for (CorpusObject each : withChildren(o).toList()) {
                        assertTrue(plugin.getObjectKinds().contains(each.getKind()),
                            plugin.getId() + " does not declare " + each.getKind());
                        checked++;
                    }
                }
            }
            plugin.getObjectKinds().forEach(k -> assertTrue(k.startsWith(plugin.getId() + ":"), k));
        }
        assertTrue(checked > 0, "the built-in grammars are on the test class path");
    }
}
