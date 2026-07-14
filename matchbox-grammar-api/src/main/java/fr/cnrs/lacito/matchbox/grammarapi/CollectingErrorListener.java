package fr.cnrs.lacito.matchbox.grammarapi;

import org.antlr.v4.runtime.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CollectingErrorListener extends BaseErrorListener {

    private final List<ParseError> errors = new ArrayList<>();
    private Path filePath;
    private String grammarId;

    public CollectingErrorListener(Path filePath, String id) {
        this.filePath = filePath;
        this.grammarId = id;
    }

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e) {
        errors.add(new ParseError(filePath, grammarId, line, charPositionInLine, msg, e, offendingSymbol, recognizer));
    }

    public List<ParseError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
