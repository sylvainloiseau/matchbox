package fr.cnrs.lacito.matchbox.grammarapi;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.IntervalSet;

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
        int startIndex = -1;
        int stopIndex = -1;
        if (offendingSymbol instanceof Token token) {
            // parser error: the offending token (at EOF, an empty range)
            startIndex = token.getStartIndex();
            stopIndex = token.getType() == Token.EOF ? startIndex - 1 : token.getStopIndex();
        } else if (recognizer instanceof Lexer lexer) {
            // lexer error: from the start of the token being matched to the rejected character
            startIndex = lexer._tokenStartCharIndex;
            // the rejected character is absent at end of input
            stopIndex = Math.min(lexer.getCharIndex(), lexer.getInputStream().size() - 1);
        }
        errors.add(new ParseError(filePath, grammarId, line, charPositionInLine, msg,
            startIndex, stopIndex, expectedTokens(recognizer, e)));
    }

    /** Display names of the tokens the parser expected; empty for lexer errors. */
    private static List<String> expectedTokens(Recognizer<?, ?> recognizer, RecognitionException e) {
        if (!(recognizer instanceof Parser parser)) {
            return List.of();
        }
        IntervalSet set;
        try {
            set = e != null ? e.getExpectedTokens() : parser.getExpectedTokens();
        } catch (RuntimeException ex) {
            return List.of();
        }
        if (set == null) {
            return List.of();
        }
        Vocabulary vocabulary = parser.getVocabulary();
        List<String> names = new ArrayList<>();
        for (int type : set.toList()) {
            names.add(type == Token.EOF ? "<EOF>" : vocabulary.getDisplayName(type));
        }
        return names;
    }

    public List<ParseError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
