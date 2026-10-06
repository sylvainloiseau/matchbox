package fr.cnrs.lacito.matchbox.grammarapi;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.IntervalSet;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.ArrayList;
import java.util.List;

/** Collects the syntax errors of an ANTLR lexer or parser as {@link Diagnostic}s. */
public class CollectingErrorListener extends BaseErrorListener {

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final TextId text;
    private final String grammarId;

    public CollectingErrorListener(TextId text, String grammarId) {
        this.text = text;
        this.grammarId = grammarId;
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
        diagnostics.add(new Diagnostic(text, grammarId, line, charPositionInLine, msg,
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

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        return !diagnostics.isEmpty();
    }
}
