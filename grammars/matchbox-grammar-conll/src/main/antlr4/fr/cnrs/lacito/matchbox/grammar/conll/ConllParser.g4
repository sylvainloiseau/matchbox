/*
 * A sentence is a block of consecutive lines containing the "# sent_id = ..." comment: the
 * comments just before it (e.g. "# newdoc id"), the comments after it, then its token lines.
 * Lines outside sentences are ignored.
 *
 * The terminals of a sentence are direct children of the sentence rule (no sub-rules): the
 * plugin reads them in order (ConllGrammarPlugin.toTree).
 */
parser grammar ConllParser;

options { tokenVocab=ConllLexer; }

sample   : (sentence | ignored)* EOF ;

sentence : (OTHER_HEADER TXT_OTHER_HEADER? (EQ_OTHER_HEADER TXT_OTHER_HEADER?)? NL_OTHER_HEADER)*
           SENT_HEADER EQ_SENT SENT_ID (NL | EOF)
           (OTHER_HEADER TXT_OTHER_HEADER? (EQ_OTHER_HEADER TXT_OTHER_HEADER?)? NL_OTHER_HEADER)*
           (TOKEN_ID (TAB_TOKEN TOKEN_FIELD?)+ NL_TOKEN)*
         ;

// lines that are not part of a sentence: blank lines, other lines, stray comments and token lines
ignored  : NL
         | OTHER_LINE (NL | EOF)
         | OTHER_HEADER TXT_OTHER_HEADER? (EQ_OTHER_HEADER TXT_OTHER_HEADER?)? NL_OTHER_HEADER
         | TOKEN_ID (TAB_TOKEN TOKEN_FIELD?)+ NL_TOKEN
         ;
