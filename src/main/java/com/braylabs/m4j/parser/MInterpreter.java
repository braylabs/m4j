package com.braylabs.m4j.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
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

	public MInterpreter(M4JProcess proc) {
		this.proc = proc;
	}
	
	@Override
	public Object visitLine(LineContext ctx) {
		
		// count its indent length
		int indent= (ctx.DOT() == null) ? 0 : ctx.DOT().size();
		
		// only execute if its indent level 0 for now
		if (indent > 0) return null;

		// if there are no commands skip it
		if (ctx.cmdList() == null) return null;

		// otherwise process command list
		return visitCmdList(ctx.cmdList());
	}
	
	@Override
	public Object visitCmdList(CmdListContext ctx) {
		
		Object ret = null;
		for (CmdContext cmd : ctx.cmd()) {
			ret = visit(cmd);
			
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
		
		if (!MCmd.COMMAND_IMPL_MAP.containsKey(name)) {
			throw new IllegalArgumentException("Command is not defined: " + name);
		}
		
		// if there is a PCE, evaluate it.  Skip the command execution if its false.
		PceContext pc = ctx.getRuleContext(PceContext.class, 0);
		if (pc != null) {
			MVal ret = (MVal) visit(pc);
			if (ret != null && !ret.isTruthy()) {
				// cancel
				return null;
			}
		}
		
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
		System.out.println("POSTFIX: " + postfix);
		
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
				return MCmdI.FALSE;
			}
		}
		
		return MCmdI.TRUE;
	}

	private Object CMD_W(CmdContext ctx) {
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
			
			// LHS of set can be 1) reference to local or global, 2) paren list of multiple values, 3) $P() function
			ParseTree lhs = expr.getChild(0);
			ParseTree oper = expr.getChild(1); // should be equals operator
			ParseTree rhs = expr.getChild(2);
			
			if (!oper.getText().equals("=")) {
				throw new RuntimeException("Expected a =");
			}
			
			if (lhs instanceof RefContext) {
				// first get the variable ref
				Object var = visitRef((RefContext) lhs);
				
				if (!(var instanceof MVar)) {
					throw new IllegalArgumentException("LHS of set must be a VAR reference");
				}

				// set the value
				Object val = visit(rhs);
				((MVar) var).set(val);
			} else {
				throw new RuntimeException();
			}
		}
		return null;
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
			ret = new MCmdQ.QuitReturn(ret);
		}
		
		return ret;
	}
	
	@Override
	public Object visitRef(RefContext ctx) {
		// resolve flags and ids
		String flags = (ctx.refFlags() == null || ctx.refFlags().FLAGS() == null) ? "" : ctx.refFlags().FLAGS().getText();
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
					throw new IllegalArgumentException("Unable to resolve: $" + id1 + " as system function or special variable");
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
				return MVal.valueOf(proxy.call("$"+id1,proc, args));
			} catch (Exception e) {
				throw new IllegalArgumentException("Error calling: $"+id1, e);
			}
		} else if (flags.equals("$$")) {
			// $$ indicates invoke a routine (w or w/o an entrypoint indicator)
			String ep = (id1 != null && id2 != null) ? id1 : null;
			String routine = (id1 != null && id2 != null) ? id2 : id1;
			
			RoutineProxy proxy = proc.getRoutine(routine);
			if (proxy == null) {
				throw new IllegalArgumentException("Routine is undefined: " + routine);
			}
			
			// invoke routine, return result
			try {
				List<MVal> args = resolveArgsToMVals(ctx.args());
				return proxy.call(ep, proc, args.toArray(new Object[] {}));
			} catch (Exception e) {
				throw new IllegalArgumentException("Error invoking ep/routine: " + ep +"/" + routine, e);
			}
		}
		
		// if flags starts with a ^ its a global, otherwise its a local
		MVar ret = null;
		if (flags.equals("^")) {
			ret = proc.getGlobal(flags + ctx.ID(0).getText());
		} else {
			ret = proc.getLocal(ctx.ID(0).getText());
		}
		
		// if its a subscripted global/var, resolve that as well
		if (ctx.args() != null) {
			for (ArgContext arg : ctx.args().arg()) {
				ret = ret.get((Comparable) visitArg(arg));
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
			return visitExpr(ctx.expr());
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
	
	public Object evalLine(String line) {
		// parse the line and then evaluate it
		MUMPSParser parser = parse(line);
		parser.removeErrorListeners();
		parser.addErrorListener(new UnderlineErrorListener());
		LinesContext ctx = parser.lines();
		
		// print debug info if set
		if (this.debug) {
			parser.addErrorListener(new DiagnosticErrorListener());
			System.out.println("Evaluating LINE: " + line);
			System.out.println("TREE: " + ctx.toStringTree(parser));
		}
		
		// actually perform the evaluation
		return visit(ctx);
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
			if (ret instanceof MCmdQ.QuitReturn) {
				ret = ((MCmdQ.QuitReturn) ret).getValue();
				
				// TODO: pop the stack vars as we are exiting the routine here
				break;
			}
		}
		
		return ret;
	}
	
	public static class UnderlineErrorListener extends BaseErrorListener {
		
		@Override
		public void syntaxError(Recognizer<?, ?> recognizer,
				Object offendingSymbol, int line, int charPositionInLine,
				String msg, RecognitionException e) {
			System.err.printf("line %s:%s %s\n", line, charPositionInLine, msg);
			underlineError(recognizer, (Token) offendingSymbol, line, charPositionInLine);
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
			System.err.println();
		}
	}
}
