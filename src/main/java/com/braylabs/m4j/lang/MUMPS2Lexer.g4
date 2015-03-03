lexer grammar MUMPS2Lexer;

tokens {COMMA,RP,AT}

// start of a pattern, switch to pattern mode
NOT_MATCH: '\'?' -> pushMode(PATTERN);
MATCH: '?' -> pushMode(PATTERN);

OPER
	: '=' | '\'=' // equivalent
	| '+' | '-' | '#' | '*' | '**' | '/' | '\\' // arithmetic
	| '>' | '>=' | '\'>' | '<' | '<=' | '\'<' // logical comparison 
	| '_' | '[' | '\'['| ']' | '\']]' | '\']' | ']]' // string 
	| '&' | '!' | '\'' // logical operators
;
LP: '(';
RP1: ')' -> type(RP);
COMMA1: ',' -> type(COMMA);
COLIN: ':';
DD: '$$';
D: '$';
UP: '^';
EXCL: '!';
AT1: '@' -> type(AT);
DOT: '.';

// whitespace
NLI: '\r'? '\n' ' '; // indented new line
NL: '\r'? '\n'; // non intended new line
WS : [ \t]+ -> skip;


ID  : [A-Za-z%][0-9A-Za-z]*; // identifier of a variable, routine, etc.
STR_LITERAL : '"' ('""'|~'"')* '"';
NUM_LITERAL // TODO: leading +/- should be valid as well but treat them as unary operators
    :   INT '.' [0-9]+ EXP? // 1.35, 1.35E-9, 0.3, -4.5
    |   INT EXP             // 1e10 -3e4
    |   INT                 // -3, 45
    |   '.' INT
    ;

fragment INT :   [0-9]+ ; // no leading zeros
fragment EXP :   [Ee] [+\-]? INT ; // \- since - means "range" inside [...]
COMMENT : ';'~[\n\r]* -> skip;

// island grammar for pattern match operator
mode PATTERN;
PAT_END: [ \t] ->popMode, skip; // space indicates end of pattern, go back to normal mode and skip this token
PAT_END2: ',' ->popMode, type(COMMA); // for W ?45,10 the comma indicates the end of pattern but should still be a comma token
PAT_END3: ')' ->popMode, type(RP);
PAT_DOT: '.';
PAT_INDR: '@' ->popMode,type(AT);
PAT_INT: [0-9]+;
PAT_LITERAL : '"' ('""'|~'"')* '"';
PAT_CODES : ([Aa] | [Cc] | [Ee] | [Ll] | [Nn] | [Pp] | [Uu])+;
