grammar MUMPS;
@lexer::members { boolean lineStart = true; }

file
	: routineHeaderLine? routineLine+ EOF
;

/* eg: VPRJ^INT^1^62896,42379.33251^0 
 * TODO: not working b/c ^INT^ is not a valid token
 * */
routineHeaderLine
	: ID '^INT^1^' NUM_LITERAL ',' NUM_LITERAL '^0' EOL
;

routineLine
	: entryPoint cmdList lineEnd // single-line entry point w/ commands
	| entryPoint lineEnd // entry point-only line
	| line
;


// TODO: Cant seem to get this to accept either EOL or EOF, it gets into infinite loop.
lines: line+;
line 
	: LEADING_WS? lineEnd // comment only line
	| LEADING_WS? DOT* cmdList lineEnd // indented line
	| LEADING_WS? cmdList COMMENT? // very last line of routine is a bit tricky, can't match EOL but could be comment, likely indented
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

lineEnd: COMMENT? EOL;

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
	: FLAGS? ID '^' ID '(' args ')' // routine ref w/ args and entry point
	| FLAGS? ID '^' ID            // routine w/ entry point reference
    | FLAGS? '^' ID '(' args ')' // global reference w/ args
    | '^' ID              		 // global reference wo/ args
    | '^(' args ')'       		// naked global reference
    | FLAGS? ID '(' args ')'     
    | FLAGS? ID
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
	| (AMBIG_OP|UNARY_OP) expr
	| expr (BIN_OP|AMBIG_OP) expr
	| expr ('?' | '\'?') exprPattern // pattern match case
	| ref
	| literal
	| '(' expr ')'
	| '(' ID (',' ID)* ')'
	;

//X1?1"^"1"[".E1"]".E
exprPattern: exprPatternItem+;
exprPatternItem: (DOT | NUM_LITERAL) (ID | STR_LITERAL)+;



// TODO: ? NUM_LITERAL not appropriate for full pattern matching operator
literal : STR_LITERAL | NUM_LITERAL	| '!';
	

BIN_OP
	: '=' | '\'=' // equiv
	| '#' | '*' | '**' | '/' | '\\' // arithmetic
	| '>' | '>=' | '\'>' | '<' | '<=' | '\'<' // logical comparison 
	| '_' | '[' | '\'['| ']' | '\']' | ']]' | '\']]' // string 
	| '&' // logical operators
;
AMBIG_OP: '-' | '+';
UNARY_OP: '\'';

EOL: '\r'? '\n' {lineStart=true;};
LEADING_WS : [ \t]+ {lineStart}? {lineStart=false;};
WS : [ \t]+ {lineStart=false;}-> channel(HIDDEN);
ID  : [A-Za-z%][0-9A-Za-z]* {lineStart=false;}; // identifier of a variable, routine, etc.
STR_LITERAL : '"' ('""'|~'"')* '"';
NUM_LITERAL // TODO: leading +/- should be valid as well but treat them as unary operators
    :   INT '.' [0-9]+ EXP? // 1.35, 1.35E-9, 0.3, -4.5
    |   INT EXP             // 1e10 -3e4
    |   INT                 // -3, 45
    ;

DOT: '.' {lineStart=false;};
FLAGS : '$$' | '$' | DOT | '@';
INTX :   '0' | [1-9] [0-9]* ; // no leading zeros
INT :   [0-9]+ ; // no leading zeros
fragment EXP :   [Ee] [+\-]? INT ; // \- since - means "range" inside [...]

COMMENT : ';'~[\n\r]* -> channel(HIDDEN);