lexer grammar conllLexer;

SENT_HEADER : '#'{getCharPositionInLine() == 1}? ' sent_id' -> pushMode(IN_SENT_HEADER) ;

OTHER_HEADER : '#'{getCharPositionInLine() == 1}? -> pushMode(IN_OTHER_HEADER) ;

TOKEN_ID : NUM{getCharPositionInLine() == 1}? NUM* -> pushMode(IN_TOKEN) ;


NUM : [0-9] ;


NL : '\r'? '\n' | '\r' ;

mode IN_TOKEN;

TOKEN_FIELD: ~[\t\n\r]+ ;

NL_TOKEN : ('\r'? '\n' | '\r') -> popMode ;

TAB_TOKEN: '\t';

mode IN_SENT_HEADER;

WS_SENT : [ ]+ -> skip ;

EQ_SENT : '=' ;

SENT_ID : ~[\r\n=]+ -> popMode ;

mode IN_OTHER_HEADER;

WS_OTHER_HEADER : [ ]+ -> skip ;

EQ_OTHER_HEADER : '=' ;

TXT_OTHER_HEADER : ~[\r\n=]+ ;

NL_OTHER_HEADER : ('\r'? '\n' | '\r') -> popMode;
