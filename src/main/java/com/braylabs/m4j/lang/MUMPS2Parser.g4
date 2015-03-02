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
	: name=ID '(' entryPointArgs? ')'
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
cmdArgList: cmdArg (':' cmdArg)*;
cmdArg: expr | '(' cmdArgList ')' | ;

// expression structure
expr
	: literal      #ExprLiteral
	| format       #ExprFormat
	| func         #ExprFunc
	| var          #ExprVar
	| ref          #ExprRef
	| indir        #ExprIndir
	| OPER expr    #ExprUnary
	| expr OPER expr #ExprBinary
	| expr '?' exprPatternItem+ #ExprMatch
	| '(' expr ')' #ExprGroup
; 

literal : STR_LITERAL | NUM_LITERAL;
format: OPER* '?' PAT_INT | OPER+; // format control characters for READ, WRITE

func: flags='$' name=ID '(' args? ')';

// indirection
indir
	: '@' ID // name indirection ("SET Y = "B",@Y = 6")
	// pattern indirection (S zipPat="5N1""-""4N"	I zip'?@zipPat W "invalid zip")
	// argument indirection (S x="var=3*4 S @x)
	// subscript indirection (S var="^BEB" W @var@(1,2,3))
	// $TEXT indirection (S L="START^MENU",LT=$TEXT(@$P(L,"^",1)^@$P(L,"^",2))
;

// variable reference (global or local) or special system variables
var
	: flags='^'? ID '(' args ')' // variable reference (local or global) w/ subscripts
	| flags='^'? ID              // variable reference (local or global) wo/ subscripts
	| flags='$' ID  // special variable ($H, etc.)
;

// ref represents a reference to routine, function, etc.
ref
	: flags='$$' ep=ID               // call entry point within current routine
	| flags='$$' ep=ID '(' args? ')' // call entry point within current routine (w/args)
	| flags='$$'? ep=ID '^' routine=ID '(' args? ')' // call routine w/ args
	| flags='$$'? ep=ID '^' routine=ID 			     // call routine wo/ args
;
args
	: expr (COMMA expr)*
	| expr ':' expr (COMMA expr ':' expr)*
	// TODO: special case for $TEXT(label+offset^routine)? 
	// or use command plugin to recognize this and not evaluate as expression?
;

exprPatternItem
	: PAT_INT (PAT_CODE | PAT_LITERAL)      // X?1"FOO"
	| PAT_INT DOT (PAT_CODE | PAT_LITERAL)  // X?1."F"
	| PAT_DOT (PAT_CODE | PAT_LITERAL)      // X?.N
	| PAT_DOT PAT_INT (PAT_CODE | PAT_LITERAL)   // X?.1"-" 
	| PAT_INT // W ?10,"INDENTED WRITE"
;

