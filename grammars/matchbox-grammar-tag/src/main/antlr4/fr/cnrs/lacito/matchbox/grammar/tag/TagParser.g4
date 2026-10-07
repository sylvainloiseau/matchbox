/* The tags of a text, in order. */
parser grammar TagParser;

options { tokenVocab = TagLexer; }

document : TAG* EOF ;
