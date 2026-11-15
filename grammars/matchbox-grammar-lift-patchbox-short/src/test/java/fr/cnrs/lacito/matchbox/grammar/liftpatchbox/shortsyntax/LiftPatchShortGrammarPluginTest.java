package fr.cnrs.lacito.matchbox.grammar.liftpatchbox.shortsyntax;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertKindsPrefixed;
import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertSpansNested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.grammar.liftpatchbox.LiftPatchKinds;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiftPatchShortGrammarPluginTest {

    private static final TextId FILE = new TextId(Path.of("notes.md"));
    private final LiftPatchShortGrammarPlugin plugin = new LiftPatchShortGrammarPlugin();

    private ParseResult parse(String text) {
        ParseResult r = plugin.getResult(FILE, text);
        assertSpansNested(r.objects(), text);
        assertKindsPrefixed(r.objects(), LiftPatchShortGrammarPlugin.ID);
        Text t = Text.builder(FILE, text).add(plugin.getId(), r.objects(), r.diagnostics()).build();
        assertEquals(r.objects().size(), t.objects().size(), () -> "invalid spans: " + t.diagnostics());
        return r;
    }

    private static List<LexiconMutationCommand> commands(ParseResult r) {
        return r.objects().stream().filter(LexiconMutationCommand.class::isInstance)
            .map(LexiconMutationCommand.class::cast).toList();
    }

    @Test
    void commandsAmongProseAreObjectsCoveringTheirText() {
        String text = "# Notes of Monday\n\nThe pig:\nc e(\"mami\", s(\"pig\"))  #\"to check\"\np /efe/door\nMore prose.\n";
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        List<LexiconMutationCommand> commands = commands(r);
        assertEquals(2, commands.size());
        assertCovers("c e(\"mami\", s(\"pig\"))", commands.get(0).getTextCoordinate(), text);
        assertEquals(4, commands.get(0).getTextCoordinate().startLine());
        assertEquals("LiftPatchShort", commands.get(0).syntax());
        assertTrue(commands.get(0).item() instanceof Command);
        assertEquals(LiftPatchShortGrammarPlugin.ID + ":" + LiftPatchKinds.CREATE, commands.get(0).getKind());
        assertCovers("p /efe/door", commands.get(1).getTextCoordinate(), text);
    }

    @Test
    void aBlockHasItsInnerCommandsAsChildren() {
        String text = "c e(\"mimi\")\n  c s(\"lizard\")\n    s (d@en = \"A lizard\")\nprose\n";
        ParseResult r = parse(text);
        LexiconMutationCommand block = commands(r).get(0);
        assertTrue(block.item() instanceof Block);
        assertEquals(LiftPatchShortGrammarPlugin.ID + ":" + LiftPatchKinds.BLOCK, block.getKind());
        assertEquals(3, block.getTextCoordinate().endLine());
        assertFalse(block.getChildren().isEmpty());
        assertEquals("c e(\"mimi\")", block.sourceText());
    }

    @Test
    void aBadLineIsAnErrorAndTheOthersStay() {
        String text = "c e(\"mami\", s(\"pig\"))\nc e(\"efe\", s(\"door\")\np /o/child\n";
        ParseResult r = parse(text);
        assertEquals(2, commands(r).size());
        Diagnostic error = r.diagnostics().get(0);
        assertEquals(Diagnostic.Category.SYNTAX, error.category());
        assertEquals(Diagnostic.Severity.ERROR, error.severity());
        assertTrue(error.code().startsWith(LexiconMutationCommand.DIAGNOSTIC_CODE_PREFIX), error.code());
        assertEquals(2, error.line());
        assertTrue(error.startIndex() >= text.indexOf("c e(\"efe\"") && error.stopIndex() < text.indexOf("p /o"));
    }

    @Test
    void aStaticErrorIsASemanticIssue() {
        String text = "s $nowhere (d@en = \"x\")\n";
        ParseResult r = parse(text);
        Diagnostic error = r.diagnostics().stream().filter(d -> d.category() == Diagnostic.Category.SEMANTIC)
            .findFirst().orElseThrow(() -> new AssertionError(r.diagnostics()));
        assertEquals("liftpatch.UNKNOWN_LABEL", error.code());
        assertEquals(Diagnostic.Severity.ERROR, error.severity());
    }

    @Test
    void windowsLineEndsGiveTheSameSpans() {
        String text = "prose\r\nc e(\"mami\")\r\nprose\r\nc e(\"efe\")\r\n";
        ParseResult r = parse(text);
        assertCovers("c e(\"efe\")", commands(r).get(1).getTextCoordinate(), text);
        assertEquals(4, commands(r).get(1).getTextCoordinate().startLine());
    }

    @Test
    void aFileWithoutCommandHasNothingAndNoWarning() {
        ParseResult r = parse("Just notes.\n\nAnd more notes.\n");
        assertTrue(r.objects().isEmpty());
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
    }

    @Test
    void theSigilAndThePragma() {
        String text = "%liftpatch 1.1 sigil=\">\"\nc e(\"notacommand\")\n> c e(\"mami\")\n";
        ParseResult r = parse(text);
        assertTrue(r.diagnostics().isEmpty(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(1, commands(r).size());
        assertCovers("c e(\"mami\")", commands(r).get(0).getTextCoordinate(), text);
        // Without a sigil, the pragma is read too, and shown as a pragma.
        String plain = "%liftpatch 1.1\nc e(\"mami\")\n";
        CorpusObject pragma = parse(plain).objects().stream().filter(o -> !(o instanceof LexiconMutationCommand))
            .findFirst().orElseThrow();
        assertCovers("%liftpatch 1.1", pragma.getTextCoordinate(), plain);
        assertEquals(LiftPatchShortGrammarPlugin.ID + ":" + LiftPatchKinds.PRAGMA, pragma.getKind());
    }

    @Test
    void aFileDeclaringTheReferenceSyntaxIsNotRead() {
        ParseResult r = parse("%liftpatch 1.1 syntax=\"LiftPatchRef\"\ncreate entry(form@tww = \"mami\")\n");
        assertTrue(r.objects().isEmpty());
        assertEquals(1, r.diagnostics().size());
        assertEquals(Diagnostic.Severity.WARNING, r.diagnostics().get(0).severity());
        assertEquals("liftpatch.OTHER_SYNTAX", r.diagnostics().get(0).code());
    }

    @Test
    void aReferenceScriptIsLeftToTheReferencePlugin() {
        ParseResult r = plugin.getResult(new TextId(Path.of("edits.liftpatchref")), "create entry(form@tww = \"mami\")\n");
        assertTrue(r.objects().isEmpty() && r.diagnostics().isEmpty());
    }

    @Test
    void theSigilActionMarksTheCommandsThatParsed() {
        String text = "c e(\"mami\")\np /a/ becomes [e] before i\n  c s(\"pig\")\n";
        ParseResult r = parse(text);
        Text t = Text.builder(FILE, text).add(plugin.getId(), r.objects(), r.diagnostics()).build();
        List<TextAction> actions = new LiftPatchSigilAction().actionsFor(t, null);
        assertEquals(1, actions.size());
        String after = text;
        List<TextEdit> edits = actions.get(0).edits();
        for (int i = edits.size() - 1; i >= 0; i--) {
            TextEdit e = edits.get(i);
            after = after.substring(0, e.start()) + e.replacement() + after.substring(e.end());
        }
        assertTrue(after.startsWith("%liftpatch 1.1 sigil=\">\"\n>c e(\"mami\")\n"), after);
        ParseResult again = parse(after);
        assertTrue(again.diagnostics().stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR),
            () -> "diagnostics: " + again.diagnostics());
        assertTrue(new LiftPatchSigilAction().actionsFor(
            Text.builder(FILE, after).add(plugin.getId(), again.objects(), again.diagnostics()).build(), null).isEmpty());
    }
}
