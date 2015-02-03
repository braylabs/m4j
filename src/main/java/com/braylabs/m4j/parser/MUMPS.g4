grammar MUMPS;
@lexer::members { boolean lineStart = true; }

file
	: routineHeaderLine? routineLine+ EOL
;

/* eg: VPRJ^INT^1^62896,42379.33251^0 */
routineHeaderLine
	: ID '^INT^1^' NUM_LITERAL ',' NUM_LITERAL '^0' EOL
;

routineLine
	: entryPoint cmdList lineEnd // single-line entry point w/ commands
	| entryPoint lineEnd // entry point-only line
	| line;

line 
	: LEADING_WS? lineEnd #CommentLine // comment only line
	| '. '+ cmdList lineEnd #IndentedLine // indented line
	| LEADING_WS? cmdList lineEnd #RegularLine // regular line
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

lineEnd: COMMENT (EOL|EOF) | EOL | EOF;

// entrypoint args must be refs, not literals
entryPoint
	: name=ID '(' epArgs ')'
	| name=ID
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
    : arg ((','|':') arg)* 
;

arg
	: STR_LITERAL
	| NUM_LITERAL
	| ref
	| expr
;

/** expressions */


exprList
	: expr ((',' | '!' | ':') expr)* // IF uses !'s, most others use ,'s, FOR uses :'s
;

/* orignal
expr
	: literal (BIN_OP expr)* #BinaryOpExpr
	| ref '=' expr         #AssignExpr
	| expr BIN_OP expr     #BinaryOpExpr
	| ref (BIN_OP expr)*   #BinaryOpExpr 
	| '(' expr ')'         #BinaryOpExpr
	| '(' ID (',' ID)* ')' #IDListExpr
	| BIN_OP expr          #BinaryOpExpr
//	| unaryop=('-' | '+' | '\'') expr    #UnaryExpr
//	| UNARY_OP expr #UnaryExpr
//	| UNARY_OP '(' expr ')' #UnaryExpr
;
 */
expr 
	// strange results with UNARY_OP expr, doesn't seem to match -1?!? but this does
	: ref '=' expr // assignment
//	| ref ('?' | '\'?') pattern // pattern match case
	| (AMBIG_OP|UNARY_OP) expr
	| expr (BIN_OP|AMBIG_OP) expr
	| ref
	| literal
	| '(' expr ')'
	| '(' ID (',' ID)* ')'
	;

//exprPattern: ('.' | NUM_LITERAL) (('A'|'C'|'E'|'L'|'N'|'P'|'U')+ | STR_LITERAL)+;



// TODO: ? NUM_LITERAL not appropriate for full pattern matching operator
literal : STR_LITERAL | NUM_LITERAL	| '!' | '?' NUM_LITERAL;
	
FLAGS : '$$' | '$' | '.' | '@';

BIN_OP
	: '='
	| '#'
	| '*'
	| '/'
	| '&'
	| '\\'
	| '>'
	| '<'
	| '_'
	| '!'
	| '\'='
;
AMBIG_OP: '-' | '+';
UNARY_OP: '\'';

EOL: '\r'? '\n' {lineStart=true;} {System.out.println("set lineStart=true");};
LEADING_WS : [ \t]+ {lineStart}? {lineStart=false;};
WS : [ \t]+ {lineStart=false;} -> channel(HIDDEN);
ID  : [A-Za-z%][0-9A-Za-z]* {lineStart=false;}; // identifier of a variable, routine, etc.
//ID_CMD : [A-Za-z]+ ; // commands cannot have numbers in them
STR_LITERAL : '"' ('""'|~'"')* '"';
NUM_LITERAL // TODO: leading +/- should be valid as well but treat them as unary operators
    :   INT '.' [0-9]+ EXP? // 1.35, 1.35E-9, 0.3, -4.5
    |   INT EXP             // 1e10 -3e4
    |   INT                 // -3, 45
    ;


INTX :   '0' | [1-9] [0-9]* ; // no leading zeros
INT :   [0-9]+ ; // no leading zeros
fragment EXP :   [Ee] [+\-]? INT ; // \- since - means "range" inside [...]

COMMENT : ';'~[\n\r]* -> channel(HIDDEN);