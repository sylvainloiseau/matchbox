package fr.cnrs.lacito.matchbox.grammar.liftpatchbox.refsyntax;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.grammar.liftpatchbox.LiftPatchKinds;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiftPatchRefGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("edits.liftpatchref"));
    private final LiftPatchRefGrammarPlugin plugin = new LiftPatchRefGrammarPlugin();

    private ParseResult parse(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertSpansNested(r.objects(), text);
        assertKindsPrefixed(r.objects(), LiftPatchRefGrammarPlugin.ID);
        Text t = Text.builder(FILE, text).add(plugin.getId(), r.objects(), r.diagnostics()).build();
        assertEquals(r.objects().size(), t.objects().size(), () -> "invalid spans: " + t.diagnostics());
        return r;
    }

    private static List<LexiconMutationCommand> commands(ParseResult r) {
        return r.objects().stream().map(LexiconMutationCommand.class::cast).toList();
    }

    @Test
    void blocksAndCommands() {
        String text = "# edits\ncreate entry(form@tww = \"mami\") {\n    create sense(gloss@en = \"pig\")\n}\n"
            + "create entry(form@tww = \"efe\")\n";
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        List<LexiconMutationCommand> commands = commands(r);
        assertEquals(2, commands.size());
        assertTrue(commands.get(0).item() instanceof Block);
        assertEquals(LiftPatchRefGrammarPlugin.ID + ":" + LiftPatchKinds.BLOCK, commands.get(0).getKind());
        assertCovers("create sense(gloss@en = \"pig\")", commands.get(0).getChildren().get(0).getTextCoordinate(), text);
        assertCovers("create entry(form@tww = \"efe\")", commands.get(1).getTextCoordinate(), text);
        assertEquals("LiftPatchRef", commands.get(1).syntax());
    }

    @Test
    void aFileOfNotesIsNotAReferenceScript() {
        TextId notes = new TextId(Path.of("notes.txt"));
        ParseResult r = plugin.getResult(notes, "Some notes.\ncreate entry(form@tww = \"mami\")\n");
        assertTrue(r.objects().isEmpty() && r.diagnostics().isEmpty(), () -> "" + r.diagnostics());
        // unless it says so
        ParseResult declared = plugin.getResult(notes, "%liftpatch 1.1 syntax=\"LiftPatchRef\"\ncreate entry(form@tww = \"mami\")\n");
        assertEquals(1, declared.objects().stream().filter(o -> o instanceof LexiconMutationCommand).count());
    }

    @Test
    void aBrokenCommandIsAnErrorAndTheNextIsKept() {
        String text = "create entry(form@tww = \"mami\"\ncreate entry(form@tww = \"efe\")\n";
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.category() == Diagnostic.Category.SYNTAX
            && d.code().startsWith(LexiconMutationCommand.DIAGNOSTIC_CODE_PREFIX)));
        assertTrue(commands(r).stream().anyMatch(c -> c.sourceText().contains("efe")), () -> "" + commands(r));
    }
}
