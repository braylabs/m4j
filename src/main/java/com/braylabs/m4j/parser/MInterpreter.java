package com.braylabs.m4j.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MUMPS;
import com.braylabs.m4j.lang.MVal;
import com.braylabs.m4j.lang.MVal.BinaryOp;
import com.braylabs.m4j.lang.MVal.UnaryOp;
import com.braylabs.m4j.lang.RoutineProxy;
import com.braylabs.m4j.parser.MCmd.MCmdI;
import com.braylabs.m4j.parser.MCmd.MCmdQ;
import com.braylabs.m4j.parser.MUMPSParser.ArgContext;
import com.braylabs.m4j.parser.MUMPSParser.ArgsContext;
import com.braylabs.m4j.parser.MUMPSParser.CmdContext;
import com.braylabs.m4j.parser.MUMPSParser.CmdListContext;
import com.braylabs.m4j.parser.MUMPSParser.EpArgsContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprListContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprPatternContext;
import com.braylabs.m4j.parser.MUMPSParser.FileContext;
import com.braylabs.m4j.parser.MUMPSParser.LineContext;
import com.braylabs.m4j.parser.MUMPSParser.LinesContext;
import com.braylabs.m4j.parser.MUMPSParser.LiteralContext;
import com.braylabs.m4j.parser.MUMPSParser.PceContext;
import com.braylabs.m4j.parser.MUMPSParser.RefContext;
import com.braylabs.m4j.parser.MUMPSParser.RoutineLineContext;

public class MInterpreter extends MUMPSBaseVisitor<Object> {

	private M4JProcess proc;
	private boolean debug;
	private MUMPSParser parser = null;
	
	public MInterpreter(M4JProcess proc) {
		this.proc = proc;
		
		// setup the parser with empty string for now
		ANTLRInputStream input = new ANTLRInputStream();
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		this.parser = new MUMPSParser(tokens);
		
		// setup error handler on the parser
		this.parser.removeErrorListeners();
		this.parser.addErrorListener(new UnderlineErrorListener());
	}
	
	@Override
	public Object visitLine(LineContext ctx) {
		
		// count its indent length
		int indent= (ctx.DOT() == null) ? 0 : ctx.DOT().size();
		
		// only execute if indent level is equal to $INDENT
		if (indent != proc.getLocal("$INDENT").valInt()) return null;

		// if there are no commands skip it
		if (ctx.cmdList() == null) return null;

		// otherwise process command list
		return visitCmdList(ctx.cmdList());
	}
	
	@Override
	public Object visitCmdList(CmdListContext ctx) {
		
		Object ret = null;
		for (CmdContext cmd : ctx.cmd()) {
			ret = visitCmd(cmd);
			
			if (ret == null) {
				// ???
			} else if (ret instanceof MCmdQ.QuitReturn) {
				// quit and return;
				return ret;
			} else if (ret == MCmdI.FALSE) {
				// returned false, stop processing this line
				break;
			}
		}

		// return the value of the last command executed
		return ret;
	}
	
	protected void printTokenWithWhitespace(ParserRuleContext ctx) {
		Token start = ctx.getStart();
		Token stop = ctx.getStop();
		
		// TODO: Can't figure out how to get ref to CommonTokenStream here
		
		CommonTokenStream stream = null;
		stream.getHiddenTokensToRight(1);
	}
	
	@Override
	public Object visitCmd(CmdContext ctx) {
		// ensure that this command is defined/implemented
		String name = ctx.ID().getText();
		
		// if there is a PCE, evaluate it.  Skip the command execution if its false.
		PceContext pc = ctx.getRuleContext(PceContext.class, 0);
		if (pc != null) {
			MVal ret = (MVal) visit(pc);
			if (ret != null && !ret.isTruthy()) {
				// cancel
				return null;
			}
		}
		
		// TODO: Make this more pluggable
		switch (name.toUpperCase()) {
			case "W":
			case "WRITE":
				return CMD_W(ctx);
			case "S":
			case "SET":
				return CMD_S(ctx);
			case "I":
			case "IF":
				return CMD_I(ctx);
			case "N":
			case "NEW":
				return CMD_N(ctx);
			case "Q":
			case "QUIT":
				return CMD_Q(ctx);
			case "F":
			case "FOR":
				return CMD_F(ctx);
			case "D":
			case "DO":
				return CMD_D(ctx);
			case "H":
				// hang has vars, halt does not, let halt flow down
				if (ctx.exprList() != null) return CMD_HANG(ctx);
			case "HALT":
				return CMD_HALT(ctx);
			case "HANG":
				return CMD_HANG(ctx);
				
			default:
				throw new IllegalArgumentException("Commmand not implemented: " + name);
		}
	}
	
	@Override
	public MVal visitLiteral(LiteralContext ctx) {
		// for string literals, strip the surrounding "'s
		if (ctx.STR_LITERAL() != null) {
			String str = ctx.STR_LITERAL().getText();
			return MVal.valueOf(str.substring(1, str.length()-1));
		}
		return MVal.valueOf(ctx.getText());
	}
	
	
	

	@Override
	public MVal visitExpr(ExprContext ctx) {
		List<Object> postfix = infixToPostfix(ctx.children);
		if (this.debug) {
			System.out.println("PostFIX: " + postfix);
		}
		
		// only 1 item? resolve it and return
		if (postfix.size() == 1) {
			Object obj = postfix.get(0);
			return MVal.valueOf((obj instanceof ParseTree) ? visit((ParseTree) obj) : obj);
		}
		
		// evaluate stack, stop when there is only 1 remaining item and return it
		for (int i=0; i < postfix.size() && postfix.size() > 1; i++) {
			// if this item is not an operator, keep going
			Object op = postfix.get(i);
			if (!(op instanceof BinaryOp) && !(op instanceof UnaryOp)) {
				continue;
//			} else if (postfix.size() < 3) {
//				throw new IllegalStateException("Expected at least 2 operands to still be on stack");
			}
			
			// we have an operator, remove it
			postfix.remove(i--);
			
			// look for an ambiguous operator scenario
			boolean ambig = i < 1 || i > postfix.size();
			
			// if its unary, pop one item off, resolve to MVal and apply operator before pushing back on stack
			if (op instanceof UnaryOp || ambig) {
				
				// if there is only 1 operand, translate ambiguous binary to unary operator
				if (ambig) {
					if (op == BinaryOp.ADD) op = UnaryOp.POS;
					else if (op == BinaryOp.SUB) op = UnaryOp.NEG;
				}
				Object lhs = postfix.remove(i--);
				MVal val1 = MVal.valueOf((lhs instanceof ParseTree) ? visit((ParseTree) lhs) : lhs);
				postfix.add(i+1, val1.apply((UnaryOp) op));
			} else {
				// for binary operator, pop 2 items off, resolve them to MVal's and apply operator before pushing back on stack
				Object rhs = postfix.remove(i--);
				Object lhs = postfix.remove(i--);
				MVal val1 = MVal.valueOf((lhs instanceof ParseTree) ? visit((ParseTree) lhs) : lhs);
				MVal val2 = MVal.valueOf((rhs instanceof ParseTree) ? visit((ParseTree) rhs) : rhs);
				
				// evaluate and push back on stack
				postfix.add(i+1, val1.apply((BinaryOp) op, val2));
			}
		}
		
		return (MVal) postfix.get(0);
	}
	
	/** eagerly consumes tokens (and subtokens) of an expression tree and converts them to postfix 
	 * TODO: --1 doesn't work right, probably need to review Shunting-yard algorithm in more detail.
	 * */
	public List<Object> infixToPostfix(List<ParseTree> infix) {
		List<ParseTree> items = new ArrayList<>(infix);
		List<Object> ret = new ArrayList<>();
		Stack<Object> ops = new Stack<>();
		
		for (int i=0; i < items.size(); i++) {
			ParseTree item = items.get(i);
			String txt = item.getText();
			
			if (item instanceof TerminalNode && (MVal.BINARY_OPS.containsKey(txt) || MVal.UNARY_OPS.containsKey(txt))) {
				
				Object newop = MVal.BINARY_OPS.get(txt);
				if (newop == null) {
					newop = MVal.UNARY_OPS.get(txt);
				}
				
				// for '(' push it on the stack, don't pop any operators
				if (newop == BinaryOp.LP) {
					ops.push(newop);
				} else if (newop == BinaryOp.RP) {
					// for ')', pop every operator until a '(', then disregard both parens
					while (!ops.isEmpty()) {
						if (ops.peek() == BinaryOp.LP) {
							// disregard and quit
							ops.pop(); 
							break;
						}
						// keep popin'
						ret.add(ops.pop());
					}
				} else if (newop instanceof MVal.UnaryOp) {
					// unary operators are right-associative and have higher precedence,
					// only pop operators off stack until a '(' or BinaryOp is encountered
					while (!ops.isEmpty() && ops.peek() != BinaryOp.LP && !(ops.peek() instanceof BinaryOp)) {
						ret.add(ops.pop());
					}
					ops.push(newop);
				} else {
					// otherwise for binary operators, pop operators off stack until a '(' and push new operator on stack
					while (!ops.isEmpty() && ops.peek() != BinaryOp.LP) {
						ret.add(ops.pop());
					}
					ops.push(newop);
				}
			} else if (item instanceof ExprContext) {
				// remove the current item, and replace it with all the children
//				ParseTree pt = (ParseTree) item;
//				for (int j=0; i < pt.getChildCount(); i++) items.add(i+j, pt.getChild(j)); 
				items.addAll(i+1,((ExprContext) item).children);
			} else {
				// literal/reference value, resolve it
				ret.add(visit(item));
			}
		}
		
		// empty stack
		while (!ops.empty()) ret.add(ops.pop());
		
		return ret;
	}

	
	private Object CMD_I(CmdContext ctx) {
		for (ExprContext expr : ctx.exprList().expr()) {
			
			// each expression should return boolean
			MVal ret = (MVal) visit(expr);
			if (ret != null && !ret.isTruthy()) {
				return MFlowControl.FALSE;
			}
		}
		
		return MFlowControl.TRUE;
	}

	private Object CMD_W(CmdContext ctx) {
		// if no expressions, list all the local vars
		if (ctx.exprList() == null) {
			Iterator<String> itr = proc.listLocals();
			while (itr.hasNext()) {
				proc.getOutputStream().println(itr.next());
			}
			return null;
		}
		
		// loop through each expression, write to output stream 
		for (ExprContext expr : ctx.exprList().expr()) {
			Object obj = visit(expr);
			
			// TODO: handle !!
			// TODO: Handle ?45 for indenting to position 45.
			if (obj.equals("!")) proc.getOutputStream().println();
			else proc.getOutputStream().print(obj);
		}
		
		return null;
	}
	
	private Object CMD_S(CmdContext ctx) {
		for (ExprContext expr : ctx.exprList().expr()) {
			CMD_S(expr);
		}
		return null;
	}
	
	/**
	 * CMD_S delegates to this, for reuse
	 * TODO: does not support the SET $E(X,*+n)="X" syntax
	 * TODO: does not support optional params of $P
	 */
	private Object CMD_S(ExprContext expr) {
		// LHS of set can be 1) reference to local or global, 2) paren list of multiple values, 3) $P() function
		ParseTree lhs = expr.getChild(0);
		ParseTree oper = expr.getChild(1); // should be equals operator
		ParseTree rhs = expr.getChild(2);
		MVal val = (MVal) visit(rhs);
		
		if (!oper.getText().equals("=")) {
			throw new RuntimeException("Expected a =");
		}
		
		if (lhs instanceof RefContext) {
			RefContext ref = (RefContext) lhs;
		
			// see if this is a special set LHS of $P or $E
			if (ref.refFlags() != null && ref.refFlags().getText().equals("$") && 
					ref.ID(0).getText().toUpperCase().startsWith("E")) {
				// bypass function execution, just get the args
				MVar var = (MVar) visitArg(ref.args().arg(0));
				MVal arg1 = MVal.valueOf(visitArg(ref.args().arg(1)));
				MVal arg2 = (ref.args().arg().size() >= 3) ? MVal.valueOf(visitArg(ref.args().arg(2))) : arg1;
				
				// replace the string with the new value
				StringBuffer sb = new StringBuffer(var.valStr());
				sb.replace(arg1.toNumber().intValue()-1, arg2.toNumber().intValue(), val.toString());
				
				// update the variable and return
				var.set(sb.toString());
				return var;
			} else if (ref.refFlags() != null && ref.refFlags().getText().equals("$") &&
					ref.ID(0).getText().toUpperCase().startsWith("P")) {
				// bypass executing the function, and get the args
				MVar var = (MVar) visitArg(ref.args().arg(0));
				String delim = MVal.valueOf(visitArg(ref.args().arg(1))).toString();
				Number from = (ref.args().arg().size() >= 3) ? MVal.valueOf(visitArg(ref.args().arg(2))).toNumber() : 1;

				// do the string replacement, update the value and return
				var.set(MUMPS.$PIECE(var.valStr(), delim, from.intValue(), val.toString()));
				return var;
			}
			
			// first get the variable reference target
			Object var = visitRef((RefContext) lhs);
			if (var instanceof MVar) {
				((MVar) var).set(val.getOrigVal());
				return var;
			}
		}
		throw new MUMPSInterpretError(lhs, "Unknown target for SET command");
	}
	
	private Object CMD_N(CmdContext ctx) {
		// collect variable name(s)
		List<String> vars = new ArrayList<>();
		for (ExprContext expr : ctx.exprList().expr()) {
			// should contain a ref, but treat it as a literal value not a variable reference
			vars.add(expr.ref().getText());
		}
		// newed all them vars!
		proc.push(false, vars.toArray(new String[]{}));
		return null;
	}
	
	private Object CMD_Q(CmdContext ctx) {
		// return the value of the first expression
		Object ret = null;
		if (ctx.exprList() != null) {
			ret = visit(ctx.exprList().expr(0));
			
			// wrap return value in marker class
			ret = new QuitReturn(ret);
		} else {
			// console mode Q should just quit
		}
		
		return ret;
	}
	
	private Object CMD_HALT(CmdContext ctx) {
		return MFlowControl.HALT;
	}
	
	/** Sleep 1 or more times for the specified seconds */
	private Object CMD_HANG(CmdContext ctx) {
		// sleep x seconds
		if (ctx.exprList() != null) {
			for (ExprContext expr : ctx.exprList().expr()) {
				Object val = (MVal) visit(expr);
				if (val != null && val instanceof MVal) {
					int sleepSecs = ((MVal) val).toNumber().intValue();
					try {
						Thread.sleep(sleepSecs * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return null;
	}
	
	private Object CMD_F(CmdContext ctx) {
		
		// F i=1:1:10 style expression
		ExprListContext exprList = ctx.exprList();
		int size = exprList.children.size();
		if (size != 5) throw new IllegalArgumentException("FOR command expected 5 tokens in command list");
		if (!exprList.getChild(1).getText().equals(":") || !exprList.getChild(3).getText().equals(":")) 
			throw new IllegalArgumentException("FOR Command didn't have the :'s as expected"); 
		
		// execute the first expression as an assignment operator, get the loop variable reference
		MVar loopVar = (MVar) CMD_S(exprList.expr(0));
		MVal inc = (MVal) visit(exprList.getChild(2));
		MVal limit = (MVal) visit(exprList.getChild(4));

		// get the parent line and determine the location of this for command
		int cmdIdx = -1;
		List<CmdContext> cmds = ((CmdListContext) ctx.getParent()).cmd();
		for (int i=0; i < cmds.size(); i++) {
			if (cmds.get(i) == ctx) {
				cmdIdx = i; break;
			}
		}
		if (cmdIdx < 0) throw new IllegalArgumentException("Unable to determine where the FOR command is in the list");

		for(;;) {
			Object ret = null;
			// execute subsequent commands on line
			for (int i=cmdIdx+1; i < cmds.size(); i++) {
				ret = visitCmd(cmds.get(i));
				
				if (ret == null) {
					// ???
				} else if (ret instanceof QuitReturn) {
					// quit within loop indicate terminate loop
					break;
				} else if (ret == MFlowControl.FALSE) {
					// returned false, stop processing this line, but continue loop
					break;
				}
			}
			
			if (ret instanceof QuitReturn) {
				break;
			}

			// do the increment
			MUMPS.$INCREMENT(loopVar, inc);
			
			// if the increment is equal to the limit, break
			if (MVal.valueOf(loopVar).apply(BinaryOp.GTE, limit).isTruthy()) {
				break;
			}
		}

		// loop has been completely executed, stop processing further commands on line (like IF statement)
		return MCmdI.FALSE;
	}
	
	/** This is only the argument-less, legacy version of DO currently */
	private Object CMD_D(CmdContext ctx) {
		FileContext file = (FileContext) ctx.getParent().getParent().getParent().getParent();
		int curLine = ctx.getStart().getLine();
		
		// use a special variable for now to track desired execution indent level
		int curIndent = MUMPS.$INCREMENT(proc.getLocal("$INDENT")).intValue();
		
		for (RoutineLineContext line : file.routineLine()) {
			// skip ahead to the next line after this DO command
			if (line.getStart().getLine() <= curLine) continue;
			
			// stop when the indent level is less than desired
			if (line.line() == null || line.line().DOT().size() < curIndent) break;
			
			// execute the line
			Object ret = visitLine(line.line());
//			System.out.println("EVAL INDENT LINE: " + line.line().toStringTree(parser));

			if (ret instanceof MCmdQ.QuitReturn) {
				break;
			}
		}
		
		// decrement the indent level
		MUMPS.$INCREMENT(proc.getLocal("$INDENT"),-1);
		
		return null;
	}
	
	@Override
	public Object visitRef(RefContext ctx) {
		// resolve flags and ids
		String flags = (ctx.refFlags() == null || ctx.refFlags().FLAGS() == null) ? "" : ctx.refFlags().FLAGS().getText();
		if (ctx.getChild(0).getText().equals("^")) {
			flags += "^";
		}
		List<TerminalNode> ids = ctx.ID();
		String id1 = (ids.size() > 0) ? ids.get(0).getText() : null;
		String id2 = (ids.size() > 1) ? ids.get(1).getText() : null;
		
		// if it starts with a $ is an intrinsic (system) function OR special variable 
		if (flags.equals("$") && id1 != null) {
			RoutineProxy proxy = proc.getRoutine("$" + id1 + "^SYS");
			if (proxy == null) {
				// can't find it as a system func, try as a special var
				MVar ret = proc.getLocal("$"+ id1);
				if (ret == null) {
					throw new MUMPSInterpretError(ctx, "Unable to resolve: $" + id1 + " as system function or special variable");
				}
				return ret;
			}
			
			// resolve args (if any)
			Object[] args = new Object[0];
			if (ctx.args() != null) {
				args = new Object[ctx.args().arg().size()];
				for (int i=0; i < args.length; i++) {
					args[i] = visitArg(ctx.args().arg(i));
				}
			}
			
			try {
				Object ret = proxy.call("$"+id1,proc, args);
				if (debug) {
					System.out.println("Output of $"+id1+" is: " + ret);
				}
				return MVal.valueOf(ret);
			} catch (Exception e) {
				throw new MUMPSInterpretError(ctx, "Error calling: $"+id1, e, true);
				
			}
		} else if (flags.equals("$$")) {
			// $$ indicates invoke a routine (w or w/o an entrypoint indicator)
			String ep = (id1 != null && id2 != null) ? id1 : null;
			String routine = (id1 != null && id2 != null) ? id2 : id1;
			MVar curRoutine = proc.getLocal("$ROUTINE");
			
			RoutineProxy proxy = proc.getRoutine(routine);
			if (proxy == null && curRoutine != null) {
				// also try looking it up as a entry point in the current routine
				proxy = proc.getRoutine(curRoutine.valStr());
				
				// if something was found, then routine is really the name of the entry point
				if (proxy != null) ep = routine;
			}
			
			if (proxy == null) {
				throw new IllegalArgumentException("Routine is undefined: " + routine);
			}
			
			// track currently executing routine as special var for now
			// then invoke routine, return result
			MVar rvar = proc.getLocal("$ROUTINE");
			String oldVal = rvar.valStr();
			try {
				List<MVal> args = resolveArgsToMVals(ctx.args());
				rvar.set(proxy.getName());
				return proxy.call(ep, proc, args.toArray(new Object[] {}));
			} catch (Exception e) {
				throw new MUMPSInterpretError(ctx, "Error invoking: " + ep + "^" + routine, e, true).setRoutine(oldVal);
			} finally {
				// restore old value
				if (oldVal != null) rvar.set(oldVal);
			}
		}
		
		// if flags starts with a ^ its a global, otherwise its a local
		MVar ret = null;
		if (flags.equals("^")) {
			ret = proc.getGlobal(id1);
		} else {
			ret = proc.getLocal(id1);
		}
		
		// if its a subscripted global/var, resolve that as well
		if (ctx.args() != null) {
			for (ArgContext arg : ctx.args().arg()) {
				MVal obj = MVal.valueOf(visitArg(arg));
				ret = ret.get(obj.toString());
			}
		}
		
		// if the variable is undefined
		
		return ret;
	}
	
	private List<MVal> resolveArgsToMVals(ArgsContext args) {
		List<MVal> ret = new ArrayList<>();
		for (ArgContext arg : args.arg()) {
			ret.add(MVal.valueOf(visitArg(arg)));
		}
		return ret;
	}
	
	@Override
	public Object visitExprPattern(ExprPatternContext ctx) {
		// for now, just returning whole pattern as a string and re-interpreting
		// the pattern as part of the operator, not sure if thats appropriate/optimal...
		return ctx.getText();
	}
	
	@Override
	public Object visitArg(ArgContext ctx) {
		// strip the "'s off string literals
		if (ctx.STR_LITERAL() != null) {
			String ret = ctx.STR_LITERAL().getText();
			return ret.substring(1, ret.length()-1);
		} else if (ctx.NUM_LITERAL() != null) {
			return Integer.parseInt(ctx.NUM_LITERAL().getText());
		} else if (ctx.ref() != null) {
			return visitRef(ctx.ref());
		} else if (ctx.expr() != null) {
			return visitExpr(ctx.expr().get(0));
		}
		throw new IllegalArgumentException("Unknown/unimplemented argument structure.");
	}
	
	// Evaluate methods -------------------------------------------------------
	
	public void setDebugMode(boolean value) {
		this.debug = value;
	}
	
	/** build and execute a lexer and parser and parse the stream */
	public static MUMPSParser parse(String stream) {
		//  build and execute a lexer and parser
		ANTLRInputStream input = new ANTLRInputStream(stream);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return new MUMPSParser(tokens);
	}
	
	/** build and execute a lexer and parser and parse the stream 
	 * @throws IOException */
	public static MUMPSParser parse(Reader reader) throws IOException {
		//  build and execute a lexer and parser
		ANTLRInputStream input = new ANTLRInputStream(reader);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return new MUMPSParser(tokens);
	}
	
	private static CommonTokenStream getTokenStream(String line) {
		ANTLRInputStream input = new ANTLRInputStream(line);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return tokens;
	}
	
	public Object evalLine(String line) {
		// parse the line and then evaluate it
		this.parser.reset();
		this.parser.setInputStream(getTokenStream(line));
		LinesContext ctx = this.parser.lines();
		
		// print debug info if set
		if (this.debug) {
			System.out.println("Evaluating LINE: " + line);
			System.out.println("TREE: " + ctx.toStringTree(parser));
		}
		
		// actually perform the evaluation (unless syntax parse errors)
		if (this.parser.getNumberOfSyntaxErrors() > 0) {
			return null;
		}
		
		try {
			return visit(ctx);
		} catch (MUMPSInterpretError err) {
			handleError(err);
		}
		return MFlowControl.ERROR;
	}
	
	public void handleError(MUMPSInterpretError err) {
		// TODO: Implement this
		throw err;
	}
	
	public Object evalRoutine(FileContext filectx, String entrypoint, Object... args) {
		Object ret = null;

		// loop through each line until we find our target entrypoint, then process 
		// subsequent lines until we find the target a quit
		// TODO: This seems horribly inefficient
		boolean exec = (entrypoint == null); // if no entrypoint specified, start executing immediately at the top
		for (RoutineLineContext rlc : filectx.routineLine()) {
			if (exec) {

				// some routine ep's can be a single line and have a command list, others have a nested line
				LineContext line = rlc.line();
				if (line == null && rlc.cmdList() != null) {
					ret = visitCmdList(rlc.cmdList());
				} else if (line != null) {
					ret = visitLine(line);
				}
				
			} else if (rlc.entryPoint() != null && rlc.entryPoint().ID().getText().equals(entrypoint)) {
				// this line is the entrypoint line matching the requested entrypoint, setup args, execute subsequent lines
				exec = true;
				
				// this is our target entrypoint, push the input variables onto the stack
				EpArgsContext epargs = rlc.entryPoint().epArgs();
				if (epargs != null) {
					String[] nudeVars = new String[epargs.ID().size()];
					for (int i=0; i < nudeVars.length; i++) {
						nudeVars[i] = epargs.ID(i).getText();
					}
					
					// NEW them before setting them
					proc.push(false, nudeVars);
					for (int i=0; i < nudeVars.length; i++) {
						MVar v = proc.getLocal(nudeVars[i]);
						v.set(args[i]);
					}
				}
				
				// if there is an associated command list, its likely a single line entrypoint, execute them
				if (rlc.cmdList() != null) {
					ret = visitCmdList(rlc.cmdList());
				}
			}
			
			// if the result is a quit, then return its return value and stop processing any further lines
			if (ret instanceof QuitReturn) {
				ret = ((QuitReturn) ret).getValue();
				
				// TODO: pop the stack vars as we are exiting the routine here
				break;
			}
		}
		
		return ret;
	}
	
	public static class MFlowControl {
		public static MFlowControl QUIT=new MFlowControl();
		public static MFlowControl ERROR=new MFlowControl();

	}
	
	public static class UnderlineErrorListener extends BaseErrorListener {
		
		@Override
		public void syntaxError(Recognizer<?, ?> recognizer,
				Object offendingSymbol, int line, int charPositionInLine,
				String msg, RecognitionException e) {
			underlineError(recognizer, (Token) offendingSymbol, line, charPositionInLine);
			System.err.printf("Syntax Error at %s:%s:  %s\n", line, charPositionInLine, msg);
		}
		
		protected void underlineError(Recognizer<?,?> recognizer, Token offendingToken, int line, int charPos) {
			// get the tokens and input stream
			CommonTokenStream tokens = (CommonTokenStream) recognizer.getInputStream();
			String input = tokens.getTokenSource().getInputStream().toString();
			
			// find and print the error line
			String[] lines = input.split("\n");
			String errorLine = lines[line-1];
			System.err.println(errorLine);
			
			// print leading space, then ^'s indicating problem token
			int start = offendingToken.getStartIndex();
			int stop = offendingToken.getStopIndex();
			for (int i=0; i <charPos; i++) System.err.print(" ");
			if (start>=0 && stop >0) {
				for (int i=start; i <= stop; i++) System.err.println("^");
			}
		}
	}
	
	/**
	 * TODO: how to fetch the original source file to display the error in its context?
	 * @author brian
	 */
	public class MUMPSInterpretError extends RuntimeException {
		private ParserRuleContext ctx;
		private String routineName;

		public MUMPSInterpretError(ParserRuleContext ctx, String msg) {
			this(ctx, msg, null, false);
		}
		
		public MUMPSInterpretError(ParserRuleContext ctx, String msg, Throwable causedBy, boolean suppress) {
			super(msg, causedBy, suppress, !suppress);
			this.ctx = ctx;
			this.routineName = MInterpreter.this.proc.getLocal("$ROUTINE").valStr();
		}
		
		public ParserRuleContext getRuleContext() {
			return ctx;
		}
		
		public MUMPSInterpretError setRoutine(String routine) {
			this.routineName = routine;
			return this;
		}
		
		@Override
		public String getMessage() {
			String ret = super.getMessage();
			Token start = getRuleContext().getStart();
			ret += " (" + routineName + "@" + start.getLine() + ":" + start.getCharPositionInLine() + ")";
			return ret;
		}
	}
}
