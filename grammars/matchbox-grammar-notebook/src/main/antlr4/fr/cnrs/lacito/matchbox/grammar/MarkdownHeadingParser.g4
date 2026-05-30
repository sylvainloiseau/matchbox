parser grammar MarkdownHeadingParser;

options { tokenVocab=MarkdownHeadingLexer; }

/*
 * Entry point
 */

document : OTHER_LINE* (section)* OTHER_LINE* EOF ;

section: HEADING (WS HEADING_CONTENT)? NL+ (yaml_block)? OTHER_LINE*;

yaml_block: YAML_START (YAML_KEY YAML_SEP YAML_VALUE NL+)+ YAML_END;
