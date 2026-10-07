/*
 * Structure of a Markdown file: an optional metadata header on line 1, then headings (each with an
 * optional metadata block) among ignored lines. The tree is flat: nesting is computed from the
 * heading levels by MarkdownHeadingVisitor.
 */
parser grammar MarkdownHeadingParser;

options { tokenVocab = MarkdownHeadingLexer; }

document      : metadata? (atxHeading | setextHeading | ignored)* EOF ;

atxHeading    : ATX_HEADING BLANK_LINE* metadata? ;

setextHeading : TEXT_LINE SETEXT_UNDERLINE BLANK_LINE* metadata? ;

metadata      : META_OPEN entry* META_CLOSE ;

entry         : META_ENTRY     # keyValue
              | META_INVALID   # unsupported
              ;

ignored       : TEXT_LINE | BLANK_LINE | RULE_LINE | FENCE_OPEN | FENCE_CLOSE ;
