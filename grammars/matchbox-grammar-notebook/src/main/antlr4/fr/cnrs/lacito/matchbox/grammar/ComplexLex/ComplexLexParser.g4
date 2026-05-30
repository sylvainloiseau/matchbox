parser grammar ComplexLexParser;

options { tokenVocab=ComplexLexLexer; }

/*
 * Entry point
 */

document : OTHER_LINE* (lex_definition)* OTHER_LINE* EOF ;

lex_definition: HEADING head gram? COLUMN def (ex ( EX_SEP ex)*)? OTHER_LINE*;

head: HEADWORD_START CONTENT HEADWORD_END;

gram: GRAM_START GRAM_CONTENT GRAM_END;

def: DEF_MARKER CONTENT DEF_MARKER;

ex : EX_START CONTENT EX_TXT_TRANSL_SEP CONTENT EX_END;
