package fr.cnrs.lacito.matchbox.assist;

import fr.cnrs.lacito.matchbox.assist.prompt.PromptPrefix;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of the edited file, built by the application for one request.
 *
 * @param file     the edited file
 * @param text     its current text (as in the editor, without provisional suggestions)
 * @param caret    offset of the cursor
 * @param grammars grammars applied to the file, in rank order
 * @param errors   parse errors of the last parse of the file
 * @param prefix   the shared prompt prefix for these grammars and this project
 */
public record AssistContext(Path file, String text, int caret, List<GrammarPlugin> grammars,
                            List<ParseError> errors, PromptPrefix prefix) {

    public AssistContext {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(text, "text");
        grammars = List.copyOf(grammars);
        errors = List.copyOf(errors);
        Objects.requireNonNull(prefix, "prefix");
    }
}
