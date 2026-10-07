lexer grammar NotebookLexer;

/*
 * ‹«❨⟨❮❯⟩❩»›
 */

@members {
    private java.util.Set<Integer> morphemeSeparators = new java.util.HashSet<>();

    public void setMorphemeSeparators(String chars) {
        morphemeSeparators.clear();
        for (int i = 0; i < chars.length(); i++) {
            morphemeSeparators.add((int) chars.charAt(i));
        }
    }

    private boolean isMorphemeSeparator() {
        return morphemeSeparators.contains(_input.LA(1));
    }

    /** True if the input starts one of the constructs of this grammar: "<", ":::" or "#LEX". */
    private boolean constructAhead() {
        int c = _input.LA(1);
        if (c == '<') return true;
        if (c == ':' && _input.LA(2) == ':' && _input.LA(3) == ':') return true;
        return c == '#' && _input.LA(2) == 'L' && _input.LA(3) == 'E' && _input.LA(4) == 'X';
    }
}

/*
 * ---- Lines that are not constructs of this grammar ----
 * Free text, Markdown headings, "---" lines, metadata of other grammars… are one token each and
 * ignored by the parser, so that the grammar can run next to other ones on the same files.
 */

OTHER_LINE : {getCharPositionInLine() == 0 && !constructAhead()}? ~[\r\n]+ ;

/*
 * ---- Tokens for single-line entries ----
 */

/**
 * IMB (intermorphemic-bloc) token.
 */

START_LEX_SIMPLE : '#LEX' -> pushMode(LEX_SIMPLE) ;

START_IMB : ':::' -> pushMode(IMB_MODE) ;
LBRACE : '{' ;
RBRACE : '}' ;
DASH   : '-' ;
TAB    : '\t' ;
QUOTE  : '"' ;
SINGLE_QUOTE : '\'' ;
START_HEADWORD : '<' {getCharPositionInLine() == 1}? -> pushMode(LEX) ;
RANGLE : '>' ;
LPARENTHESIS : '(' ;
RPARENTHESIS : ')' ;
SHARP : '#' ;

HEADER
    : SHARP+ ' ';

/*
ENTRY_WORD
    : ~[\r\n>]+
    ;

DEF_TEXT
    : ~[\r\n}]+
    ;

EXAMPLE_WORD
    : ~[\t\r\n ]+
    ;

// Single-line content token (exclude quote chars so quoted lines become QUOTED_LINE)
LINE_CONTENT : ~[<'" \r\n] ~[\r\n]+ ;
*/

// Quoted line including surrounding quotes (single or double)

/*
 * ---- Newlines & whitespace ----
 */

NEWLINE : '\r'? '\n' | '\r' ;

WS
    : [ ]+ -> skip
    ;


/** <foo> {- another definition} */

mode HEAD;

WS_HEAD
    : [ ]+ -> skip
    ;

TXT_HEAD : ~[\r\n]+ ;

END_HEAD : ('\r'? '\n' | '\r') -> popMode;

mode LEX;

WS_LEX
    : [ ]+ -> skip
    ;

TXT : ~[\r\n>{}/]+ ;
END_HEADWORD : '>' ;
START_DEF : '{' -> pushMode(DEF_MODE);


END_LEX : ('\r'? '\n' | '\r') -> popMode;

mode DEF_MODE;

END_DEF : '}' -> popMode ;
TXT_DEF : ~[\r\n}«]+ ;
START_EX : '«' -> pushMode(EXAMPLE_MODE);

mode EXAMPLE_MODE;

WS_EX : [ ]+ -> skip ;

TXT_EX : ~[\r\n/«»}]+ ;
SEP_EX : '/';
END_EX : '»' -> popMode;

mode LEX_SIMPLE; // ‹førmiʒɛr›	()	:	‘fourmilière’

LEX_SIMPLE_GLOSS : '\'' ~[']* '\'';
LEX_SIMPLE_CAT : '(' ~[)]* ')';
LEX_SIMPLE_FORM : '‹' ~[›]+ '›';
WS_LEX_SIMPLE : [ ]+ -> skip ;
END_LEX_SIMPLE : ('\r'? '\n' | '\r') -> popMode;

mode IMB_MODE;

END_IMB : ':::' -> popMode ;

IMB_QUOTED_LINE
    : '\'' {getCharPositionInLine() == 1}? ~[\r\n]+ '\''
    ;

IMB_LINE
    : ~['\r\n] ~[\r\n]+
    ;

IMB_NL : '\r'? '\n' | '\r' ;

/*
MORPHEME_LINE
    : MORPHEM (MORPH_SEP MORPHEM)+
    ;

MORPH_SEP
    : { isMorphemeSeparator() }? ~[\t\r\n]
    ;

fragment MORPHEM: MORPHEME_CHAR+;

MORPHEME_CHAR
    : { ! isMorphemeSeparator() }? ~[\t\r\n]
    ;
 */
// https://stackoverflow.com/questions/42058127/how-can-i-check-if-first-character-of-a-line-is-in-antlr4
