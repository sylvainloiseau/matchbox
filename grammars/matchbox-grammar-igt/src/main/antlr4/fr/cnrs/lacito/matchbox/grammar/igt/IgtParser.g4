/* A block is a start anchor, its lines, and an end anchor (missing when the block is not closed). */
parser grammar IgtParser;

options { tokenVocab = IgtLexer; }

document : (block | OTHER_LINE)* EOF ;

block : START (BODY | BLANK)* END? ;
