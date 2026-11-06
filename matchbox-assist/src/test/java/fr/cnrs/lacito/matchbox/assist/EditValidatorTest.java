package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EditValidatorTest {

    private static final TextId FILE = new TextId(Path.of("f.txt"));
    private static final List<GrammarPlugin> GRAMMARS = List.of(new BracketGrammar());
    private final EditValidator validator = new EditValidator();

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void acceptsAFixAndCountsFixedErrors() {
        String text = "a [b\n\nc";
        EditValidator.Result r = validator.check(FILE, text, List.of(TextEdit.insert(4, "]")), GRAMMARS);
        assertTrue(r.accepted());
        assertEquals(1, r.fixedErrors());
    }

    @Test
    void rejectsAnEditThatAddsAnError() {
        String text = "a [b]\n\nc";
        EditValidator.Result r = validator.check(FILE, text, List.of(TextEdit.insert(1, " ]")), GRAMMARS);
        assertFalse(r.accepted());
        assertEquals(1, r.newErrors().size());
    }

    @Test
    void rejectsAnErrorAddedLaterInTheSameParagraph() {
        // the edit itself is fine, but the rest of its paragraph no longer parses
        String text = "a b\nc]\n\nz";
        String withOpening = "a [b\nc]\n\nz";
        assertEquals(1, new BracketGrammar().getResult(FILE, text).diagnostics().size());
        EditValidator.Result r = validator.check(FILE, text, List.of(TextEdit.insert(2, "[")), GRAMMARS);
        assertFalse(r.accepted(), () -> "errors of " + withOpening + ": " + r);
    }

    @Test
    void acceptsAnIncompleteBlockThatStaysIncomplete() {
        // completion inside an unfinished block: the existing error remains, none is added
        String text = "a [b";
        assertTrue(validator.check(FILE, text, List.of(TextEdit.insert(4, " c")), GRAMMARS).accepted());
    }

    @Test
    void validatesOnlyTheParagraphOfLargeTexts() {
        String filler = "ok\n\n".repeat(60_000);
        String text = filler + "a [b\n\nend";
        int at = filler.length() + 4;
        EditValidator.Result r = validator.check(FILE, text, List.of(TextEdit.insert(at, "]")), GRAMMARS);
        assertTrue(r.accepted());
        assertEquals(1, r.errorsBefore());
        assertEquals(0, r.errorsAfter());
    }

    /** One exclusive object per occurrence of a word, written in the line. */
    private static GrammarPlugin wordGrammar(String id, String word) {
        return new GrammarPlugin() {
            @Override public String getId() { return id; }
            @Override public String getDisplayName() { return id; }
            @Override public String getDisplayDescription() { return ""; }
            @Override public String getFormatDocumentation() { return ""; }
            @Override public List<fr.cnrs.lacito.matchbox.grammarapi.GrammarExample> getExamples() { return List.of(); }
            @Override public String getGrammarSource() { return ""; }
            @Override public int getApiVersion() { return API_VERSION; }

            @Override
            public fr.cnrs.lacito.matchbox.grammarapi.ParseResult getResult(TextId text, String content) {
                List<fr.cnrs.lacito.matchbox.corpus.CorpusObject> found = new java.util.ArrayList<>();
                for (int i = content.indexOf(word); i >= 0; i = content.indexOf(word, i + 1)) {
                    found.add(new fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT(id + ":igt",
                        new fr.cnrs.lacito.matchbox.corpus.TextCoordinate(text, i, i + word.length() - 1, 1, 1), "t", "m", "f"));
                }
                return new fr.cnrs.lacito.matchbox.grammarapi.ParseResult(found, List.of());
            }
        };
    }

    @Test
    void refusesAnEditMakingAReadingDisappear() {
        // "pig" is read by the first grammar, "igloo" by the second: writing "pigloo" makes them meet
        List<GrammarPlugin> grammars = List.of(wordGrammar("a", "pig"), wordGrammar("b", "igloo"));
        String text = "p igloo\n";
        EditValidator.Result r = validator.check(FILE, text, List.of(TextEdit.replace(text, 1, 2, "")), grammars);
        assertFalse(r.accepted(), r.toString());
        assertEquals(1, r.newErrors().size());
        assertTrue(r.newErrors().get(0).isWarning());
        // an edit elsewhere is accepted, even when the text already has a reading not kept
        String already = "pigloo\n\nother\n";
        assertTrue(validator.check(FILE, already, List.of(TextEdit.insert(already.length(), "more")), grammars).accepted());
    }
}
