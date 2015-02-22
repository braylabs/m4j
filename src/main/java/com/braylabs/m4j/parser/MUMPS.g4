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

// TODO: Probably would be easier if we collapsed routineLine as alternate styles of line
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
	| LEADING_WS? DOT* COMMENT? lineEnd // indented comment line
	| LEADING_WS? DOT* cmdList COMMENT? // very last line of routine is a bit tricky, can't match EOL but could be comment, likely indented
;

cmdList
	: cmd (cmd)*
;

cmd
	: ID pce? exprList
	| ID pce?
	| ID pce? cmdArgList // for commands line OPEN, USE, CLOSE, READ
;

cmdArgList
	: cmdArg (':' cmdArg)* 
;

cmdArg
	: expr 
	| '(' cmdArgList ')'
	| 
;

pce 
	: ':' exprList
;

lineEnd: COMMENT? EOL;

// entrypoint args must be refs, not literals
entryPoint
	: name=ID '(' epArgs? ')'
	| name=ID
;

epArgs
	: ID (',' ID)*
;

// TODO: Maybe indirection @ should not be part of ref, instead a form of expression?
/** REFS are functions, routines, globals, locals, etc (but not commands) */
ref 
	: refFlags? ID '^' ID '(' args ')' // routine ref w/ args and entry point
	| refFlags? ID '^' ID            // routine w/ entry point reference
    | refFlags? '^' ID '(' args ')' // global reference w/ args
    | '^' ID              		 // global reference wo/ args
    | '^(' args ')'       		// naked global reference
    | refFlags? ID '@'+ // indirection
    | refFlags? ID '(' args? ')' '@'+ // indirection @$$CURNODE()@
    | refFlags? ID '@' '(' args ')' // indirection
    | '@' '(' args ')' // indirection
    | refFlags? ID '(' args ')'     
    | refFlags? ID
;

refFlags
	: '@'? FLAGS // regular $ or $$ with optional indirect reference
	| '@' 
	| DOT;

args
    : arg (',' arg)* 
;

arg
	: STR_LITERAL
	| NUM_LITERAL
	| ref
	| expr
	| expr ':' expr
	| // empty place holder arg
;

/** expressions */


// TODO: :'s list should probably be commandParams or something instead of an exprList
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
	| '?' NUM_LITERAL // weird indenting expressions for W (ie: ?35)
	| '(' expr ')'
	| '(' ID (',' ID)* ')'
	;

//X1?1"^"1"[".E1"]".E
exprPattern: exprPatternItem+;
exprPatternItem: (DOT | NUM_LITERAL) (ID | STR_LITERAL)+;

literal : STR_LITERAL | NUM_LITERAL	| '!'+;
	

BIN_OP
	: '=' | '\'=' // equiv
	| '#' | '*' | '**' | '/' | '\\' // arithmetic
	| '>' | '>=' | '\'>' | '<' | '<=' | '\'<' // logical comparison 
	| '_' | '[' | '\'['| ']' | '\']]' | '\']' | ']]' // string 
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
FLAGS : '$$' | '$' | '@';
INTX :   '0' | [1-9] [0-9]* ; // no leading zeros
INT :   [0-9]+ ; // no leading zeros
fragment EXP :   [Ee] [+\-]? INT ; // \- since - means "range" inside [...]

COMMENT : ';'~[\n\r]* -> channel(HIDDEN);