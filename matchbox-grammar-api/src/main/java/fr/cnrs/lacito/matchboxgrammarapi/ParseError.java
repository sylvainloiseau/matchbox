package fr.cnrs.lacito.matchboxgrammarapi;

import java.nio.file.Path;

import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class ParseError {
        public final int line;
        public final int charPositionInLine;
        public final String message;
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

        public ParseError(Path file, String grammarId, int line, int charPositionInLine, String message, RecognitionException e, Object offendingSymbol, Recognizer<?,?> recognizer) {
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.message = message;
            this.e = e;
            this.offendingSymbol = offendingSymbol;
            this.recognizer = recognizer;
            this.file = file;
            this.grammarId = grammarId;
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