parser grammar MUMPS2Parser;
options { tokenVocab=MUMPS2Lexer; }

file: entryPoint line+ EOF;
line
	: NL entryPoint cmd+  // single-line entry point w/ commands
	| NL entryPoint       // entry point-only line
	| NLI DOT* cmd*      // typical line (from routine file)
	| NL? DOT* cmd+		     // typical line (from console)
;
lines: line+; // for console/tests to parse multiple lines

entryPoint
	: name=ID LP entryPointArgs? RP
	| name=ID
;
entryPointArgs: ID (COMMA ID)*;

// command structure
cmd
	: ID cmdPostCond? expr (COMMA expr)* // regular command with expression list
	| ID cmdPostCond? cmdArgList       // for commands with arguments like OPEN, FOR, USE, CLOSE, READ, etc.
	| ID cmdPostCond?                  // command with no expressions/arguments
;
cmdPostCond: ':' expr (COMMA expr)*;
cmdArgList: cmdArg (':' cmdArg)*; // OPEN, FOR style
cmdArg: expr | LP cmdArgList RP | ;

// expression structure
expr
	: literal      #ExprLiteral
	| format       #ExprFormat
	| func         #ExprFunc
	| var          #ExprVar
	| ref          #ExprRef
	| AT LP expr RP #ExprIndrExpr
	| AT ref (AT LP args? RP)? #ExprIndrRef
	| AT var (AT LP args? RP)? #ExprIndrVar
	| OPER expr     #ExprUnary
	| expr OPER expr #ExprBinary
	| expr (MATCH | NOT_MATCH) exprPatternItem+ #ExprMatch
	| LP expr RP #ExprGroup
	| tag=ID (OPER n=NUM_LITERAL)? ('^' routine=ID)?  #ExprLineLabel // line label reference for $T(TAG+N^ROUTINE), GO F1: command, etc.
	| tag=ID OPER LP expr RP ('^' routine=ID)? #ExprLineLabel2
; 

literal : STR_LITERAL | NUM_LITERAL;
format: OPER* '?' PAT_INT | OPER+; // format control characters for READ, WRITE

func: flags='$' name=ID LP args? RP;

// indirection
/*
indir
	: AT ref (AT LP args? RP)? #IndrSubscr // subscript indirection (S var="^BEB" W @var@(1,2,3))
	| AT var (AT LP args? RP)? #IndrSubscr
	| AT LP expr RP #IndrExpr // expression indirection within parens)
	// $TEXT indirection (S L="START^MENU",LT=$TEXT(@$P(L,"^",1)^@$P(L,"^",2))
;
*/

// variable reference (global or local) or special system variables
var
	: flags=(DOT | '^')? ID LP args RP // variable reference (local or global) w/ subscripts
	| flags=(DOT | '^')? ID            // variable reference (local or global) wo/ subscripts
	| flags='^' LP args RP             // naked global reference
	| flags='$' ID  // special variable ($H, etc.)
;

// ref represents a reference to routine, function, etc.
ref
	: flags='$$' ep=ID               // call entry point within current routine
	| flags='$$' ep=ID LP args? RP // call entry point within current routine (w/args)
	| flags='$$'? ep=ID '^' routine=ID LP args? RP // call routine w/ args
	| flags='$$'? ep=ID '^' routine=ID 			     // call routine wo/ args
;
args
	: expr (COMMA expr)*
	| expr ':' expr (COMMA expr ':' expr)*
;

exprPatternItem
	: PAT_INT (PAT_CODES | PAT_LITERAL)      // X?1"FOO"
	| PAT_INT DOT (PAT_CODES | PAT_LITERAL)  // X?1."F"
	| PAT_DOT (PAT_CODES | PAT_LITERAL)      // X?.N
	| PAT_DOT PAT_INT (PAT_CODES | PAT_LITERAL)   // X?.1"-" 
	| PAT_INT // W ?10,"INDENTED WRITE"
	| AT expr // pattern indirection (S zipPat="5N1""-""4N"	I zip'?@zipPat W "invalid zip")
;

