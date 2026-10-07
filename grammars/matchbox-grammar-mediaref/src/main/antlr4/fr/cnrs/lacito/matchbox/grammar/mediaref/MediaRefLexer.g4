/*
 * Media markers: lines "@media [<file>] [<start>-<end>] [<caption>]" (a file, a range or both).
 * Every other line is one OTHER_LINE token, ignored by the parser, so the grammar can run next to
 * other grammars on the same files.
 *
 * Reading rules: after "@media", a range if the text has the form of one, otherwise a file; after
 * the file, a range if the text has the form of one, otherwise the caption. A file name that looks
 * like a range must be quoted.
 */
lexer grammar MediaRefLexer;

tokens { CAPTION }

@members {
    private static final String TIME = "(?:\\d+|(?:\\d+:)?\\d+:\\d{2}(?:\\.\\d{1,3})?)";
    private static final java.util.regex.Pattern RANGE_AHEAD =
        java.util.regex.Pattern.compile(TIME + "[ \\t]*[-\u2013][ \\t]*" + TIME + "(?=[ \\t]|$)");

    /** The rest of the current line, from the current position. */
    private String restOfLine() {
        StringBuilder b = new StringBuilder();
        for (int i = 1; ; i++) {
            int c = _input.LA(i);
            if (c == org.antlr.v4.runtime.IntStream.EOF || c == '\n' || c == '\r') return b.toString();
            b.append((char) c);
        }
    }

    /** True if a time range starts here, followed by a space or the end of the line. */
    private boolean rangeAhead() {
        return RANGE_AHEAD.matcher(restOfLine()).lookingAt();
    }

    /** True at the start of a line (the column is not reset after a lone '\r', so look at the previous character). */
    private boolean atLineStart() {
        int previous = _input.LA(-1);
        return _input.index() == 0 || previous == '\n' || previous == '\r';
    }

    private static boolean isBreak(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == org.antlr.v4.runtime.IntStream.EOF;
    }

    /** True if the line starts with the "@media" keyword. */
    private boolean markerAhead() {
        String start = "@media";
        for (int i = 0; i < start.length(); i++) {
            if (_input.LA(i + 1) != start.charAt(i)) return false;
        }
        return isBreak(_input.LA(start.length() + 1));
    }

    /** A last marker line without line ending still gets its MARKER_END token. */
    @Override
    public Token emitEOF() {
        if (_mode == DEFAULT_MODE) {
            return super.emitEOF();
        }
        Token end = _factory.create(_tokenFactorySourcePair, MARKER_END, "", DEFAULT_TOKEN_CHANNEL,
            _input.index(), _input.index() - 1, getLine(), getCharPositionInLine());
        while (_mode != DEFAULT_MODE) {
            popMode();
        }
        emit(end);
        return end;
    }
}

MEDIA_START : {atLineStart()}? '@media' {isBreak(_input.LA(1))}? [ \t]* -> pushMode(MARKER) ;
OTHER_LINE  : {atLineStart() && !markerAhead()}? ~[\r\n]+ ;
NL          : '\r'? '\n' | '\r' ;

// first element after "@media": a range or a file
mode MARKER;

RANGE       : {rangeAhead()}? TIME [ \t]* ('-' | '\u2013') [ \t]* TIME -> mode(CAPTION_MODE) ;
QUOTED_FILE : '"' ('\\' ~[\r\n] | ~["\\\r\n])* '"' -> mode(AFTER_FILE) ;
FILE        : ~[ \t\r\n"]+ -> mode(AFTER_FILE) ;
MARKER_WS   : [ \t]+ -> skip ;
MARKER_END  : ('\r'? '\n' | '\r') -> popMode ;

// after the file: a range or the caption
mode AFTER_FILE;

AFTER_FILE_WS      : [ \t]+ -> skip ;
AFTER_FILE_RANGE   : {rangeAhead()}? TIME [ \t]* ('-' | '\u2013') [ \t]* TIME -> type(RANGE), mode(CAPTION_MODE) ;
AFTER_FILE_CAPTION : {!rangeAhead()}? ~[ \t\r\n] ~[\r\n]* -> type(CAPTION) ;
AFTER_FILE_END     : ('\r'? '\n' | '\r') -> type(MARKER_END), popMode ;

// after the range: the caption
mode CAPTION_MODE;

CAPTION_WS   : [ \t]+ -> skip ;
CAPTION_TEXT : ~[ \t\r\n] ~[\r\n]* -> type(CAPTION) ;
CAPTION_END  : ('\r'? '\n' | '\r') -> type(MARKER_END), popMode ;

fragment TIME : [0-9]+ | ([0-9]+ ':')? [0-9]+ ':' [0-9] [0-9] ('.' [0-9] ([0-9] [0-9]?)?)? ;
