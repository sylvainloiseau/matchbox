package fr.cnrs.lacito.matchbox.grammar.notebook;

import static fr.cnrs.lacito.matchbox.corpus.CorpusAssertions.assertCovers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
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
        SimpleIGT igt = objectsOf(r, SimpleIGT.class).get(0);
        assertCovers("a mio-jaflo-m-wi-j", igt.field(InterlinearSentence.TRANSCRIPTION).orElseThrow().span(), TEXT);
        assertCovers("1sg down-canoe-top-m.sg-pred", igt.field(InterlinearSentence.MORPHEMES).orElseThrow().span(), TEXT);
        assertCovers("I went down in canoe", igt.field(InterlinearSentence.FREE_TRANSLATION).orElseThrow().span(), TEXT);
        assertEquals("I went down in canoe", igt.field(InterlinearSentence.FREE_TRANSLATION).orElseThrow().value());
        SimpleLexicalDefinition mami = objectsOf(r, SimpleLexicalDefinition.class).get(0);
        assertCovers("mami", mami.field(LexicalEntry.HEADWORD).orElseThrow().span(), TEXT);
    }

    @Test
    void settingAFieldChangesOnlyThatValue() {
        ParseResult r = plugin.getResult(FILE, TEXT);
        SimpleIGT igt = objectsOf(r, SimpleIGT.class).get(0);
        EditProposal p = plugin.getObjectEditor().setField(igt, InterlinearSentence.FREE_TRANSLATION, "I went by canoe", TEXT);
        String edited = TextEdit.applyAll(TEXT, assertInstanceOf(EditProposal.Edits.class, p).edits());

        ParseResult after = plugin.getResult(FILE, edited);
        assertTrue(after.diagnostics().isEmpty(), () -> "errors: " + after.diagnostics());
        assertEquals("'I went by canoe'", objectsOf(after, SimpleIGT.class).get(0).getFreeTranslation());
        assertEquals(List.of("mami", "jaflo"),
            objectsOf(after, SimpleLexicalDefinition.class).stream().map(SimpleLexicalDefinition::getHeadword).toList());
    }

    @Test
    void valuesBreakingTheNotationAreRefused() {
        ParseResult r = plugin.getResult(FILE, TEXT);
        SimpleLexicalDefinition mami = objectsOf(r, SimpleLexicalDefinition.class).get(0);
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(mami, LexicalEntry.HEADWORD, "ma>mi", TEXT));
        SimpleIGT igt = objectsOf(r, SimpleIGT.class).get(0);
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(igt, InterlinearSentence.MORPHEMES, "a\nb", TEXT));
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(igt, InterlinearSentence.TRANSCRIPTION, ":::", TEXT));
        assertInstanceOf(EditProposal.Refused.class,
            plugin.getObjectEditor().setField(igt, "unknown", "x", TEXT));
    }
}
