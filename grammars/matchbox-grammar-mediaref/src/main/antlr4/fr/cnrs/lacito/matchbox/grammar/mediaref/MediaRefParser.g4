/*
 * Media markers among ignored lines: "@media" then a file and/or a range, then an optional caption.
 */
parser grammar MediaRefParser;

options { tokenVocab = MediaRefLexer; }

document : (marker | ignored)* EOF ;

marker   : MEDIA_START ( (FILE | QUOTED_FILE) RANGE? | RANGE ) CAPTION? MARKER_END ;

ignored  : OTHER_LINE NL? | NL ;
