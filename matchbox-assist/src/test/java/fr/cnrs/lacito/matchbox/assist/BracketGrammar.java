package fr.cnrs.lacito.matchbox.assist;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Test grammar: on each line, every '[' must be closed by ']'. Errors imitate ANTLR messages:
 * "missing ']'" at the end of the line, "extraneous input ']'" on an unopened ']'.
 */
public class BracketGrammar implements GrammarPlugin {

    public String getId() { return "brackets"; }
    public String getDisplayName() { return "Brackets"; }
    public String getDisplayDescription() { return "Square brackets must be balanced on each line."; }
    public String getFormatDocumentation() { return "Write [x] to mark a gloss."; }
    public List<GrammarExample> getExamples() { return List.of(new GrammarExample("a gloss", "dog [animal]")); }
    public String getGrammarSource() { return "grammar Brackets;"; }
    public int getApiVersion() { return API_VERSION; }

    public ParseResult getResult(Path file, String content) {
        List<ParseError> errors = new ArrayList<>();
        int lineStart = 0;
        int line = 1;
        for (String l : content.split("\n", -1)) {
            int depth = 0;
            for (int i = 0; i < l.length(); i++) {
                char c = l.charAt(i);
                if (c == '[') depth++;
                if (c == ']') {
                    if (depth == 0) {
                        errors.add(new ParseError(file, getId(), line, i, "extraneous input ']' expecting <EOF>",
                            lineStart + i, lineStart + i, List.of("<EOF>")));
                    } else {
                        depth--;
                    }
                }
            }
            if (depth > 0) {
                errors.add(new ParseError(file, getId(), line, l.length(), "missing ']' at '\\n'",
                    lineStart + l.length(), lineStart + l.length(), List.of("']'")));
            }
            lineStart += l.length() + 1;
            line++;
        }
        return new ParseResult(List.of(), errors);
    }
}
