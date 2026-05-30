lexer grammar MarkdownHeadingLexer;

HEADING : '#' {getCharPositionInLine() == 1}? '#'?;
HEADING_CONTENT : ~[\r\n]+;
YAML_START : '---' [\r\n];
YAML_END : '---' [\r\n];
YAML_SEP : ':';
YAML_KEY : ~[:\r\n]+ ;
YAML_VALUE : ~[:\r\n]+;
OTHER_LINE : ~[\r\n]+ [\r\n];
NL : [\r\n];
WS : ' ';
