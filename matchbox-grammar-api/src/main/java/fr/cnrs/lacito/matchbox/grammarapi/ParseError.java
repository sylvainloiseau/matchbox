package fr.cnrs.lacito.matchbox.grammarapi;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class ParseError {
        public final int line;
        public final int charPositionInLine;
        public final String message;
        private final int startIndex;
        private final int stopIndex;
        private final List<String> expected;
        private RecognitionException e;
        private Object offendingSymbol;
        private Recognizer<?, ?> recognizer;
        private Path file;
        public Path getFile() {
            return file;
        }

        public String getGrammarId() {
            return grammarId;
        }

        private String grammarId;

        /**
         * Error without ANTLR objects attached. Prefer this constructor: keeping the recognizer
         * or the exception keeps the whole parser and its token stream in memory.
         */
        public ParseError(Path file, String grammarId, int line, int charPositionInLine, String message) {
            this(file, grammarId, line, charPositionInLine, message, -1, -1, List.of());
        }

        /**
         * Error located by offsets, without ANTLR objects attached.
         *
         * @param startIndex offset of the first character of the offending input (0-based), -1 if
         *                   unknown; may be the length of the text for an error at end of file
         * @param stopIndex  offset of its last character (inclusive), -1 if unknown;
         *                   {@code startIndex - 1} for an empty range (e.g. at end of file)
         * @param expected   tokens expected at this position, as displayed by ANTLR: the literal
         *                   between quotes when the token has one (e.g. {@code "'<'"}), its
         *                   symbolic name otherwise (e.g. {@code "IMB_LINE"}), {@code "<EOF>"}
         */
        public ParseError(Path file, String grammarId, int line, int charPositionInLine, String message,
                          int startIndex, int stopIndex, List<String> expected) {
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.message = message;
            this.startIndex = startIndex;
            this.stopIndex = stopIndex;
            this.expected = List.copyOf(expected);
            this.file = file;
            this.grammarId = grammarId;
        }

        public ParseError(Path file, String grammarId, int line, int charPositionInLine, String message, RecognitionException e, Object offendingSymbol, Recognizer<?,?> recognizer) {
            this(file, grammarId, line, charPositionInLine, message);
            this.e = e;
            this.offendingSymbol = offendingSymbol;
            this.recognizer = recognizer;
        }

        @Override
        public String toString() {
            return "line " + line + ":" + charPositionInLine + " " + message;
        }

        public Integer getLine() {
            return Integer.valueOf(line);
        }

        public Integer getCharPositionInLine() {
            return Integer.valueOf(charPositionInLine);
        }

        public String getMessage() {
            return message;
        }

        /** Offset of the first character of the offending input, -1 if unknown. */
        public int getStartIndex() {
            return startIndex;
        }

        /** Offset of the last character of the offending input (inclusive), -1 if unknown. */
        public int getStopIndex() {
            return stopIndex;
        }

        /** Tokens expected at this position (see the constructor); empty if unknown. */
        public List<String> getExpected() {
            return expected;
        }

        /**
         * Span of the offending input, if known and not empty. The end line is the start line:
         * offending tokens are assumed not to span several lines.
         */
        public Optional<TextCoordinate> span() {
            if (startIndex < 0 || stopIndex < startIndex) {
                return Optional.empty();
            }
            return Optional.of(new TextCoordinate(file, startIndex, stopIndex, line, line));
        }

        public RecognitionException getE() {
            return e;
        }

        public Object getOffendingSymbol() {
            return offendingSymbol;
        }

        public Recognizer<?, ?> getRecognizer() {
            return recognizer;
        }

    }
