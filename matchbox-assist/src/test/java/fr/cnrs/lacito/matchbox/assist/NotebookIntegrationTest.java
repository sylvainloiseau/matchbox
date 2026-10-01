package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.assist.suggestion.GrammarQuickFixes;
import fr.cnrs.lacito.matchbox.assist.suggestion.TextEdit;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The quick fixes and the validator with the notebook grammar. */
class NotebookIntegrationTest {

    private static final Path FILE = Path.of("notes.txt");
    private static final List<GrammarPlugin> GRAMMARS = List.of(new NotebookGrammarPlugin());

    @Test
    void quickFixCandidatesAreCheckedWithTheRealGrammar() {
        String text = "<jaflo> {canoe}\n\n:::\na mio\n:::\n";
        List<ParseError> errors = GRAMMARS.get(0).getResult(FILE, text).errors();
        assertFalse(errors.isEmpty());
        try (EditValidator validator = new EditValidator()) {
            List<String> report = new ArrayList<>();
            for (ParseError e : errors) {
                for (GrammarQuickFixes.Fix fix : GrammarQuickFixes.candidates(text, e)) {
                    EditValidator.Result r = validator.check(FILE, text, List.of(fix.edit()), GRAMMARS);
                    report.add(e.getMessage() + " -> " + fix.title() + ": " + r);
                    // never accepted if it adds errors
                    if (r.accepted()) {
                        assertTrue(r.errorsAfter() <= r.errorsBefore(), report::toString);
                    }
                }
            }
            System.out.println(String.join("\n", report));
        }
    }

    @Test
    void unicodeLiteralsOfTheGrammarAreOfferedAsInsertions() {
        String text = "<jaflo> {canoe\n";
        ParseError eof = GRAMMARS.get(0).getResult(FILE, text).errors().stream()
            .filter(e -> e.getMessage().startsWith("mismatched input '<EOF>'")).findFirst().orElseThrow();
        assertTrue(GrammarQuickFixes.candidates(text, eof).stream().anyMatch(f -> f.title().equals("Insert '\u00AB'")),
            () -> GrammarQuickFixes.candidates(text, eof).toString());
    }

    @Test
    void completingAnEntryIsAcceptedAndBreakingItIsNot() {
        String text = "<jaflo> {canoe}\n";
        try (EditValidator validator = new EditValidator()) {
            assertTrue(validator.check(FILE, text, List.of(TextEdit.insert(text.length(), "\n<mami> {pig}\n")), GRAMMARS)
                .accepted());
            // removing the closing brace breaks the entry
            assertFalse(validator.check(FILE, text, List.of(TextEdit.replace(text, 14, 15, "")), GRAMMARS).accepted());
        }
    }
}
