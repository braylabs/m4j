parser grammar MUMPS2Parser;
options { tokenVocab=MUMPS2Lexer; }

file: entryPoint line+ EOF;

line
	: NL entryPoint cmd+  // single-line entry point w/ commands
	| NL entryPoint       // entry point-only line
	| NLI DOT* cmd*  // typical line (from routine file)
	| cmd+		     // typical line (from console)
;

entryPoint
	: name=ID '(' entryPointArgs? ')'
	| name=ID
;
entryPointArgs: ID (',' ID)*;

cmd
	: ID cmdPostCond? expr? (',' expr)* // regular command with expression list
	| ID cmdPostCond? cmdArgList        // for commands with arguments like OPEN, FOR, USE, CLOSE, READ, etc.
;
cmdPostCond: ':' expr (',' expr)*;
cmdArgList: cmdArg (':' cmdArg)*;
cmdArg: expr | '(' cmdArgList ')' | ;

expr
	: literal      #ExprLiteral
	| func         #ExprFunc
	| var          #ExprVar
	| ref          #ExprRef
	| OPER expr    #ExprUnary
	| expr OPER expr #ExprBinary
	| expr '?' exprPatternItem+ PAT_END #ExprMatch
	| '(' expr ')' #ExprGroup
; 
literal : QMARK PAT_INT PAT_END | STR_LITERAL | NUM_LITERAL	| OPER+; // operators for !!, ?45

func: flags='$' name=ID '(' args? ')';

// variable reference (global or local) or special system variables
var
	: flags='^'? ID '(' args ')' // variable reference (local or global) w/ subscripts
	| flags='^'? ID              // variable reference (local or global) wo/ subscripts
	| flags='$' ID  // special variable ($H, etc.)
;

// ref represents a reference to routine, function, etc.
ref
	: flags='$$' ep=ID               #RefRoutine // call entry point within current routine
	| flags='$$' ep=ID '(' args? ')' #RefRoutine // call entry point within current routine (w/args)
	| flags='$$'? ep=ID '^' routine=ID '(' args? ')' #RefRoutine // call routine w/ args
	| flags='$$'? ep=ID '^' routine=ID 			     #RefRoutine // call routine wo/ args
;
args
	: expr (',' expr)*   #ArgsExprList
	| expr ':' expr (',' expr ':' expr)*   #ArgsParamList
;

exprPatternItem
	: PAT_INT (PAT_CODE | STR_LITERAL)      // X?1"FOO"
	| PAT_INT DOT (PAT_CODE | STR_LITERAL)  // X?1."F"
	| PAT_DOT (PAT_CODE | STR_LITERAL)      // X?.N
	| PAT_DOT PAT_INT (PAT_CODE | STR_LITERAL)   // X?.1"-" 
	| PAT_INT // W ?10,"INTENTED WRITE"
;

