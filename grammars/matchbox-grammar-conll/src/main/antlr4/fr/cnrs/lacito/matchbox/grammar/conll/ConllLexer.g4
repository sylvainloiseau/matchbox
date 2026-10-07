/*
 * CoNLL-U lines. Only lines that can belong to a sentence get their own tokens: comment lines
 * ("# key = value", "# sent_id = ..."), token lines (an ID - "1", "1-2" or "1.1" - followed by a
 * tab). Every other line (free text, Markdown...) is one OTHER_LINE token, ignored by the parser,
 * so the grammar can run on files that are not pure CoNLL-U and next to other grammars.
 */
lexer grammar ConllLexer;

@members {
    /** True if the line starting here is a comment line or a token line (ID followed by a tab). */
    private boolean conllLineAhead() {
        if (_input.LA(1) == '#') return true;
        int i = 1;
        while (Character.isDigit(_input.LA(i))) i++;
        if (i == 1) return false;
        if (_input.LA(i) == '-' || _input.LA(i) == '.') {
            int j = i + 1;
            while (Character.isDigit(_input.LA(j))) j++;
            if (j == i + 1) return false;
            i = j;
        }
        return _input.LA(i) == '\t';
    }

    /**
     * A last line without line ending still gets its end-of-line token (NL_TOKEN for a token line,
     * NL_OTHER_HEADER for a comment), so that it is read like the other lines; then EOF.
     */
    @Override
    public Token emitEOF() {
        int type = switch (_mode) {
            case IN_TOKEN -> NL_TOKEN;
            case IN_OTHER_HEADER, IN_OTHER_VALUE -> NL_OTHER_HEADER;
            default -> -1;
        };
        if (type == -1) {
            return super.emitEOF();
        }
        Token end = _factory.create(_tokenFactorySourcePair, type, "", DEFAULT_TOKEN_CHANNEL,
            _input.index(), _input.index() - 1, getLine(), getCharPositionInLine());
        while (_mode != DEFAULT_MODE) {
            popMode();
        }
        emit(end);
        return end;
    }
}

// "# sent_id = ...": the mandatory comment that starts the identification of a sentence
SENT_HEADER  : {getCharPositionInLine() == 0}? '#' [ \t]* 'sent_id' [ \t]* {_input.LA(1) == '='}? -> pushMode(IN_SENT_HEADER) ;

// any other comment line ("# text = ...", "# newdoc id = ...", "# a comment")
OTHER_HEADER : {getCharPositionInLine() == 0}? '#' -> pushMode(IN_OTHER_HEADER) ;

// a token line: its ID, then the tab-separated fields
TOKEN_ID     : {getCharPositionInLine() == 0}? [0-9]+ ([-.] [0-9]+)? {_input.LA(1) == '\t'}? -> pushMode(IN_TOKEN) ;

// every other line
OTHER_LINE   : {getCharPositionInLine() == 0 && !conllLineAhead()}? ~[\r\n]+ ;

NL : '\r'? '\n' | '\r' ;

mode IN_TOKEN;

TOKEN_FIELD : ~[\t\n\r]+ ;
TAB_TOKEN   : '\t' ;
NL_TOKEN    : ('\r'? '\n' | '\r') -> popMode ;

mode IN_SENT_HEADER;

WS_SENT : [ \t]+ -> skip ;
EQ_SENT : '=' ;
SENT_ID : ~[ \t\r\n=] ~[\r\n]* -> popMode ;

mode IN_OTHER_HEADER;

WS_OTHER_HEADER  : [ \t]+ -> skip ;
// the value (after the first '=') may contain '='
EQ_OTHER_HEADER  : '=' -> pushMode(IN_OTHER_VALUE) ;
TXT_OTHER_HEADER : ~[ \t\r\n=] ~[\r\n=]* ;
NL_OTHER_HEADER  : ('\r'? '\n' | '\r') -> popMode ;

mode IN_OTHER_VALUE;

WS_OTHER_VALUE   : [ \t]+ -> skip ;
OTHER_VALUE      : ~[ \t\r\n] ~[\r\n]* -> type(TXT_OTHER_HEADER), popMode ;
NL_OTHER_VALUE   : ('\r'? '\n' | '\r') -> type(NL_OTHER_HEADER), popMode, popMode ;
