parser grammar conllParser;

options { tokenVocab=conllLexer; }

sample : blankLines* sentence (blankLines sentence)* blankLines* EOF ;

blankLines
    : NL+
    ;
	
sentence : SENT_HEADER EQ_SENT SENT_ID blankLines+
           (OTHER_HEADER TXT_OTHER_HEADER EQ_OTHER_HEADER TXT_OTHER_HEADER NL_OTHER_HEADER blankLines*)*
           (TOKEN_ID (TAB_TOKEN TOKEN_FIELD)+ NL_TOKEN blankLines*)* ;

