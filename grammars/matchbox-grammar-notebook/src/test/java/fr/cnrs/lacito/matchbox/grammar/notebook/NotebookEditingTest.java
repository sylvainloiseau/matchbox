package fr.cnrs.lacito.matchbox.grammar.notebook;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Field spans and the notebook {@code ObjectEditor}: edit a field, parse again, check the result. */
class NotebookEditingTest {

    private static final TextId FILE = new TextId(Path.of("sample.txt"));
    private static final String TEXT = """
        <mami> {pig} {pork}

        :::
        a mio-jaflo-m-wi-j
        1sg down-canoe-top-m.sg-pred
        'I went down in canoe'
        :::

        <jaflo> {canoe}
        """;

    private final NotebookGrammarPlugin plugin = new NotebookGrammarPlugin();

    private static <T> List<T> objectsOf(ParseResult r, Class<T> type) {
        return r.objects().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void fieldSpansCoverTheValuesWithoutDelimiters() {
        ParseResult r = plugin.getResult(FILE, TEXT);
        SimpleLexicalDefinition mami = objectsOf(r, SimpleLexicalDefinition.class).get(0);
        assertCovers("mami", mami.field(LexicalEntry.HEADWORD).orElseThrow().span(), TEXT);
        assertTrue(r.objects().stream().noneMatch(o -> o.getKind().endsWith(":igt")), "IGTs are read by the IGT grammar");
    }

    @Test
    void settingAFieldChangesOnlyThatValue() {
        ParseResult r = plugin.getResult(FILE, TEXT);
        SimpleLexicalDefinition jaflo = objectsOf(r, SimpleLexicalDefinition.class).get(1);
        EditProposal p = plugin.getObjectEditor().setField(jaflo, LexicalEntry.HEADWORD, "jaflu", TEXT);
        String edited = TextEdit.applyAll(TEXT, assertInstanceOf(EditProposal.Edits.class, p).edits());

        ParseResult after = plugin.getResult(FILE, edited);
        assertTrue(after.diagnostics().isEmpty(), () -> "errors: " + after.diagnostics());
        assertEquals(List.of("mami", "jaflu"),
            objectsOf(after, SimpleLexicalDefinition.class).stream().map(SimpleLexicalDefinition::getHeadword).toList());
        assertTrue(edited.contains("1sg down-canoe-top-m.sg-pred"), "the IGT block is untouched");
    }

    @Test
    void valuesBreakingTheNotationAreRefused() {
        ParseResult r = plugin.getResult(FILE, TEXT);
        SimpleLexicalDefinition mami = objectsOf(r, SimpleLexicalDefinition.class).get(0);
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(mami, LexicalEntry.HEADWORD, "ma>mi", TEXT));
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(mami, LexicalEntry.HEADWORD, " ", TEXT));
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(mami, "unknown", "x", TEXT));
    }
}
