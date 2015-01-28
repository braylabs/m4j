grammar MUMPS;
file
	: routineHeaderLine? line+
;

/* eg: VPRJ^INT^1^62896,42379.33251^0 */
routineHeaderLine
	: ID '^INT^1^' NUM_LITERAL ',' NUM_LITERAL '^0' EOL
;

line 
	: entryPoint cmdList lineEnd #LineEPAndCommands // single-line entry point w/ commands
	| entryPoint lineEnd #LineEPOnly // entry point-only line 
	| lineEnd #CommentLine // comment only line
	| '. '+ cmdList lineEnd #IndentedLine // indented line
	| cmdList lineEnd #RegularLine // regular line
;

cmdList
	: cmd (cmd)*
;

cmd
	: ID pce? exprList
	| ID pce?
;

pce 
	: ':' expr
;

lineEnd
	: COMMENT EOL
	| EOL
;

// entrypoint args must be refs, not literals
entryPoint
	: name=ID '(' epArgs ')'
//	| name=ID
;

epArgs
	: ID (',' ID)*
;

/** REFS are functions, routines, globals, locals, etc (but not commands) */
ref 
	: FLAGS? ID '^' ID '(' args ')'
    | FLAGS? ID '(' args ')'
    | FLAGS? '^' ID '(' args ')' // global reference w/ args
    | FLAGS? '^' ID // global reference wo/ args
	| FLAGS? ID '^' ID
    | FLAGS? ID
    | '^(' args ')' // naked global reference
;

args
    : arg (',' arg)* 
;

arg
	: STR_LITERAL
	| NUM_LITERAL
	| ref
	| expr
;

FLAGS 
	  : '$$'
	  | '$'
	  | '.'
	  | '@'
;

/** expressions */

exprList
	: expr ((',' | '!' | ':') expr)* // IF uses !'s, most others use ,'s, FOR uses :'s
;

expr
	: literal (BIN_OP expr)* #BinaryOpExpr
	| ref '=' expr         #AssignExpr
	| ref (BIN_OP expr)*   #BinaryOpExpr 
	| '(' expr ')'         #NestedExpr
	| '(' ID (',' ID)* ')' #IDListExpr
	| UNARY_OP expr #UnaryExpr
;

BIN_OP
	: '+'
	| '-'
	| '='
	| '#'
	| '*'
	| '/'
	| '\\'
	| '>'
	| '<'
	| '_'
	| '!'
	| '\'='
;

UNARY_OP
	: '\''
	| '-'
	| '+'
;

literal : STR_LITERAL | NUM_LITERAL	| '!' | '?' NUM_LITERAL;

EOL 
	:'\r'? '\n'
	| EOF
;
DOT : '.'; // dot
WS : [ \t]+ -> channel(HIDDEN);
ID  : [A-Za-z%][0-9A-Za-z]* ; // identifier of a variable, routine, etc.
ID_CMD : [A-Za-z]+ ; // commands cannot have numbers in them
STR_LITERAL
	: '"'~['"']*'"'
;
NUM_LITERAL // TODO: leading +/- should be valid as well but treat them as unary operators
    :   INT '.' [0-9]+ EXP? // 1.35, 1.35E-9, 0.3, -4.5
    |   INT EXP             // 1e10 -3e4
    |   INT                 // -3, 45
    ;


INTX :   '0' | [1-9] [0-9]* ; // no leading zeros
INT :   [0-9]+ ; // no leading zeros
fragment EXP :   [Ee] [+\-]? INT ; // \- since - means "range" inside [...]

COMMENT
	: ';'~[\n\r]*
;