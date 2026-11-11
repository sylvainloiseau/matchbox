/*
 * IGT blocks: a start anchor line ":::" (optionally followed by a {…} metadata block), body lines,
 * an end anchor line ":::". Every other line is one OTHER_LINE token, ignored by the parser, so the
 * grammar can run next to other grammars on the same files. Pandoc divs are recognised and left
 * alone: "::: note" or "::: {.class}" opens a div, and the next bare ":::" closes it (not an IGT).
 * Whole-line tokens: the metadata block and the lines are read by the Java code.
 */
lexer grammar IgtLexer;

@members {
    private static final java.util.regex.Pattern START_LINE =
        java.util.regex.Pattern.compile(":::[ \\t]*(\\{.*\\})?[ \\t]*");
    private static final java.util.regex.Pattern END_LINE = java.util.regex.Pattern.compile(":::[ \\t]*");
    private static final java.util.regex.Pattern DIV_OPEN_LINE =
        java.util.regex.Pattern.compile(":::+[ \\t]*(\\{[ \\t]*[.#].*|[^{:\\s].*)");
    private static final java.util.regex.Pattern BARE_FENCE = java.util.regex.Pattern.compile(":::+[ \\t]*");

    /** Pandoc divs open and not yet closed. */
    private int divDepth;

    private boolean divOpenAhead() {
        return atLineStart() && DIV_OPEN_LINE.matcher(restOfLine()).matches();
    }

    private boolean divCloseAhead() {
        return divDepth > 0 && atLineStart() && BARE_FENCE.matcher(restOfLine()).matches();
    }

    /** The rest of the current line, from the current position. */
    private String restOfLine() {
        StringBuilder b = new StringBuilder();
        for (int i = 1; ; i++) {
            int c = _input.LA(i);
            if (c == org.antlr.v4.runtime.IntStream.EOF || c == '\n' || c == '\r') return b.toString();
            b.append((char) c);
        }
    }

    private boolean atLineStart() {
        int previous = _input.LA(-1);
        return _input.index() == 0 || previous == '\n' || previous == '\r';
    }

    private boolean startAhead() {
        return atLineStart() && START_LINE.matcher(restOfLine()).matches();
    }

    private boolean endAhead() {
        return atLineStart() && END_LINE.matcher(restOfLine()).matches();
    }
}

DIV_OPEN : {divOpenAhead()}? ':::' ~[\r\n]* EOL? {divDepth++;} -> type(OTHER_LINE) ;
DIV_CLOSE : {divCloseAhead()}? ':::' ~[\r\n]* EOL? {divDepth--;} -> type(OTHER_LINE) ;
START : {startAhead()}? ':::' ~[\r\n]* EOL? -> pushMode(BLOCK) ;
OTHER_LINE : ~[\r\n]+ EOL? | EOL ;

fragment EOL : '\r\n' | '\n' | '\r' ;

mode BLOCK;

END : {endAhead()}? ':::' ~[\r\n]* EOL? -> popMode ;
NEXT_START : {startAhead()}? ':::' ~[\r\n]* EOL? -> type(START) ;
BODY : ~[\r\n]+ EOL? ;
BLANK : EOL ;
