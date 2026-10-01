package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.assist.suggestion.TextEdit;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EditValidatorTest {

    private static final Path FILE = Path.of("f.txt");
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
        assertEquals(1, new BracketGrammar().getResult(FILE, text).errors().size());
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
}
