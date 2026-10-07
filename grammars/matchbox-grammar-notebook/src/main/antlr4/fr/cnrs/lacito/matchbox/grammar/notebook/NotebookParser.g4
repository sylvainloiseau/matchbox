parser grammar NotebookParser;
options { tokenVocab=NotebookLexer; }

/*
 * Entry point
 */

// constructs of the grammar among lines it ignores (free text, Markdown, other grammars' notations)
document : (paragraph | otherLine | NEWLINE)* EOF ;

otherLine : OTHER_LINE NEWLINE? ;

/*
 * Paragraph can be one of three forms
 */

paragraph
    : singleLineEntry
    | lexSimpleEntry
    | igt_paragraph
    ;

lexSimpleEntry
    : START_LEX_SIMPLE LEX_SIMPLE_FORM LEX_SIMPLE_CAT LEX_SIMPLE_GLOSS END_LEX_SIMPLE
    ;

singleLineEntry
    : START_HEADWORD TXT END_HEADWORD sense* END_LEX
    ;

sense
    : START_DEF TXT_DEF example* END_DEF
    ;

example
  : START_EX TXT_EX SEP_EX TXT_EX END_EX
  ;

/*
 * ---- Form A: three-line group ----
 */

// three consecutive lines with last line a quoted line
// Groups separated by optional blank lines
igt_paragraph
    : START_IMB IMB_NL IMB_LINE IMB_NL IMB_LINE IMB_NL IMB_QUOTED_LINE IMB_NL END_IMB NEWLINE
    ;

/*
 * ---- Form C: anything else ----
 */

/*
otherContent
    : LINE_CONTENT NEWLINE
    | NEWLINE
    ;
*/

/*
 * Definitions and examples
 */


/*
 * Shared structures
 */
/*
morpheme
    : MORPHEME
    ;
 */
 
blankLines
    : NEWLINE+
    ;
	

