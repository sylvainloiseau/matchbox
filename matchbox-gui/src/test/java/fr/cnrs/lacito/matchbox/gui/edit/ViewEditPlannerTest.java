package fr.cnrs.lacito.matchbox.gui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewEditPlannerTest {

    private static final String TEXT = """
        <mami> {pig}

        :::
        a mio-jaflo-m-wi-j
        1sg down-canoe-top-m.sg-pred
        'I went down in canoe'
        :::
        """;

    @TempDir
    Path dir;
    private final GrammarPlugin grammar = new NotebookGrammarPlugin();
    private final EditValidator validator = new EditValidator();
    private final Corpus corpus = new Corpus();
    private TextDocument document;
    private SimpleIGT igt;

    @BeforeEach
    void setUp() throws Exception {
        document = new TextDocument(Files.writeString(dir.resolve("notes.txt"), TEXT));
        ParseResult r = grammar.getResult(document.getId(), TEXT);
        corpus.put(Text.builder(document.getId(), TEXT).add(grammar.getId(), r.objects(), r.diagnostics()).build());
        igt = corpus.text(document.getId()).orElseThrow().objectsOf(SimpleIGT.class).get(0);
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    private ViewEditPlanner planner(boolean validate) {
        return new ViewEditPlanner(corpus,
            id -> id.equals(document.getId()) ? Optional.of(document) : Optional.empty(),
            d -> List.of(grammar), validate ? validator : null);
    }

    private List<TextEdit> setTranslation(String value) {
        EditProposal p = grammar.getObjectEditor().setField(igt, InterlinearSentence.FREE_TRANSLATION, value, TEXT);
        return assertInstanceOf(EditProposal.Edits.class, p).edits();
    }

    @Test
    void editsOfAnUpToDateTextAreReadyAsTheyAre() {
        List<TextEdit> edits = setTranslation("I went by canoe");
        var ready = assertInstanceOf(ViewEditPlanner.Ready.class, planner(true).prepare(igt, edits));
        assertEquals(edits, ready.edits());
        assertEquals(document, ready.document());
    }

    @Test
    void editsAreRelocatedAfterTypingNotYetParsed() {
        List<TextEdit> edits = setTranslation("I went by canoe");
        document.applyEdits(List.of(TextEdit.insert(0, "% note\n")), EditOrigin.of(EditOrigin.Kind.TYPING, null));
        var ready = assertInstanceOf(ViewEditPlanner.Ready.class, planner(true).prepare(igt, edits));
        assertEquals(edits.get(0).start() + 7, ready.edits().get(0).start());
    }

    @Test
    void editsOfAChangedValueAreStale() {
        List<TextEdit> edits = setTranslation("I went by canoe");
        String text = document.getCurrentText();
        int at = text.indexOf("I went down");
        document.applyEdits(List.of(TextEdit.replace(text, at, at + 11, "We went up")),
            EditOrigin.of(EditOrigin.Kind.TYPING, null));
        var failed = assertInstanceOf(ViewEditPlanner.Failed.class, planner(true).prepare(igt, edits));
        assertInstanceOf(EditOutcome.Stale.class, failed.outcome());
    }

    @Test
    void editsAddingParseErrorsAreRefusedUnlessAllowed() {
        int at = TEXT.indexOf("1sg");
        // a line starting with a quote breaks the IGT block
        List<TextEdit> breaking = List.of(TextEdit.replace(TEXT, at, at + 3, "'1sg"));
        var failed = assertInstanceOf(ViewEditPlanner.Failed.class, planner(true).prepare(igt, breaking));
        assertInstanceOf(EditOutcome.Invalid.class, failed.outcome());
        assertInstanceOf(ViewEditPlanner.Ready.class, planner(false).prepare(igt, breaking));
    }

    @Test
    void objectsOfAnOutdatedParseAreStale() {
        corpus.put(Text.builder(document.getId(), TEXT).build());
        var failed = assertInstanceOf(ViewEditPlanner.Failed.class,
            planner(true).prepare(igt, setTranslation("x")));
        assertInstanceOf(EditOutcome.Stale.class, failed.outcome());
    }

    @Test
    void textActionsArePreparedForTheWholeText() {
        var markdown = new fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin();
        ParseResult r = markdown.getResult(document.getId(), TEXT);
        corpus.put(Text.builder(document.getId(), TEXT).add(markdown.getId(), r.objects(), r.diagnostics()).build());
        var actions = new fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingActions()
            .actionsFor(corpus.text(document.getId()).orElseThrow(), null);
        assertEquals(1, actions.size(), "no header: 'Add header' offered");
        var ready = assertInstanceOf(ViewEditPlanner.Ready.class,
            new ViewEditPlanner(corpus, id -> Optional.of(document), d -> List.of(markdown), validator)
                .prepare(document.getId(), actions.get(0).edits()));
        assertEquals("---\n---\n", ready.edits().get(0).replacement());
        var unknown = new ViewEditPlanner(new Corpus(), id -> Optional.of(document), d -> List.of(), null)
            .prepare(document.getId(), actions.get(0).edits());
        assertInstanceOf(ViewEditPlanner.Failed.class, unknown);
    }
}
