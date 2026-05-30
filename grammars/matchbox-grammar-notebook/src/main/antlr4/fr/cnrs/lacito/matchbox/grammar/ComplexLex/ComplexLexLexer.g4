lexer grammar ComplexLexLexer;

/*
#LEX ‹bralɑ›	(v.)	:	‘faire sécher’ «le vã k a sufla a nø a bralɑ le lɛ̃ʒ mɛ w e pa asɛ se / le vent qui a souflé cet nuit a fait sêcher le linge mais il n'est ps sec.» ; «l ɛrb kə nə koperã ajɛr ~ jɛr lə vã da mati l a bralad for la fanɑ (a) ke sɛr / l'herbe que nous avons coupée hier, le vent l'a séché ce matin, il faut la fanner ce soir.'.» [#Sans sujet : cf. "fo sortir la wej"]
*/

HEADING : '#LEX ' -> pushMode(LEX_MODE);
OTHER_LINE : ~[\r\n]+ [\r\n];

mode LEX_MODE;

END : [\r\n] -> popMode ;

HEADWORD_START : '‹';
HEADWORD_END : '›';
GRAM_START : '(' -> pushMode(GRAM_MODE);
COLUMN : ':';
DEF_MARKER : '‘';
EX_START : '«';
EX_END : '»';
EX_GLOS_SEP : '/';

CONTENT : ~[\r\n‹›«»/‘]+;
// HEADWORD : ~[‹›\r\n]+;
// DEF : ~[\r\n‘]+;
// EX : ~[\r\n«»/]+;

mode GRAM_MODE;

GRAM_CONTENT : ~[\r\n)]+;
GRAM_END : ')' -> popMode;
