/*
 * Inline tags: '#' followed by a keyword between straight quotes ("...") or curly quotes, e.g.
 * #"verb/infinitive", anywhere in a line, several per line. Everything else is skipped; nothing is
 * read inside fenced code blocks (``` or ~~~, closed by a fence of the same character, at least as
 * long; an unclosed fence runs to the end of the text).
 */
lexer grammar TagLexer;

@members {
    private char fenceChar;
    private int fenceLength;

    /** True at the start of a line (looks at the previous character: the column is not reset after a lone '\r'). */
    private boolean atLineStart() {
        int previous = _input.LA(-1);
        return _input.index() == 0 || previous == '\n' || previous == '\r';
    }

    private void fenceOpen() {
        String line = getText().stripLeading();
        fenceChar = line.charAt(0);
        int n = 0;
        while (n < line.length() && line.charAt(n) == fenceChar) n++;
        fenceLength = n;
    }

    /** Closes the fence if this line is its closing fence. */
    private void fenceLine() {
        String line = getText();
        String trimmed = line.strip();
        int indent = line.length() - line.stripLeading().length();
        if (indent <= 3 && trimmed.length() >= fenceLength && trimmed.chars().allMatch(c -> c == fenceChar)) {
            popMode();
        }
    }
}

TAG        : '#' ('"' ~["\r\n]* '"' | '“' ~[”\r\n]* '”') ;
FENCE_OPEN : {atLineStart()}? ' '? ' '? ' '? ('```' '`'* | '~~~' '~'*) ~[\r\n]* { fenceOpen(); } -> pushMode(FENCE), skip ;
NL         : ('\r'? '\n' | '\r') -> skip ;
TEXT       : ~[#\r\n`~]+ -> skip ;
OTHER      : . -> skip ;

mode FENCE;

FENCE_CONTENT : ~[\r\n]+ { fenceLine(); } -> skip ;
FENCE_NL      : ('\r'? '\n' | '\r') -> skip ;
