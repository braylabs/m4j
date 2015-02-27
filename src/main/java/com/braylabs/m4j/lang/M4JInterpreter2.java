package com.braylabs.m4j.lang;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdPostCondContext;
import com.braylabs.m4j.lang.MUMPS2Parser.EntryPointArgsContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprBinaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprFuncContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprGroupContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLiteralContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprUnaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprVarContext;
import com.braylabs.m4j.lang.MUMPS2Parser.FileContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LineContext;
import com.braylabs.m4j.lang.MUMPS2Parser.VarContext;
import com.braylabs.m4j.lang.MVal.BinaryOp;
import com.braylabs.m4j.parser.MInterpreter;
import com.braylabs.m4j.parser.MInterpreter.MFlowControl;
import com.braylabs.m4j.parser.MInterpreter.MUMPSInterpretError;
import com.braylabs.m4j.parser.MInterpreter.QuitReturn;
import com.braylabs.m4j.parser.MInterpreter.UnderlineErrorListener;

public class M4JInterpreter2 extends MUMPS2ParserBaseVisitor<Object> {
	private M4JProcess proc;
	private boolean debug;
	private MUMPS2Parser parser = null;
	private Map<String,CMDHandler> commands = new HashMap<>();

	public M4JInterpreter2(M4JProcess proc) {
		this.proc = proc;
		
		// setup the parser with empty string for now
		ANTLRInputStream input = new ANTLRInputStream();
		MUMPS2Lexer lexer = new MUMPS2Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		this.parser = new MUMPS2Parser(tokens);
		
		// setup error handler on the parser
		this.parser.removeErrorListeners();
		this.parser.addErrorListener(new UnderlineErrorListener());
		
		// setup standard command handlers
		// TODO: This probably belongs in M4JRuntime somewhere?
		commands.putAll(new WriteCMDHandler().getCollection());
		commands.putAll(new DoCMDHandler().getCollection());
		commands.putAll(new SetCMDHandler().getCollection());
		commands.putAll(new IfCMDHandler().getCollection());

	}
	
	@Override
	public Object visitLine(LineContext ctx) {
		
		// count its indent length
		int indent= (ctx.DOT() == null) ? 0 : ctx.DOT().size();
		
		// only execute if indent level is equal to $INDENT
		if (indent != proc.getLocal("$INDENT").valInt()) return null;
		
		// if this is an entry point, go through the args
		// (TODO: currently just for unit test sake)
		if (ctx.entryPoint() != null) {
			visitEntryPoint(ctx.entryPoint());
		}

		// if there are no commands skip it
		if (ctx.cmd().isEmpty()) return null;

		// otherwise process command list
		Object ret = null;
		for (CmdContext cmd : ctx.cmd()) {
			ret = visitCmd(cmd);
			
			if (ret == null) {
				// ???
			} else if (ret instanceof QuitReturn) {
				// quit and return;
				return ret;
			} else if (ret == MFlowControl.FALSE) {
				// returned false, stop processing this line
				break;
			}
		}

		// return the value of the last command executed
		return ret;
	}

	//--------------------------------------	
	
	@Override
	public Object visitCmd(CmdContext ctx) {
		// ensure that this command is defined/implemented
		String name = ctx.ID().getText().toUpperCase();
		
		// if there is a PCE, evaluate it.  Skip the command execution if its false.
		CmdPostCondContext pce = ctx.cmdPostCond();
		if (pce != null) {
			MVal ret = (MVal) visit(pce);
			if (ret != null && !ret.isTruthy()) {
				// cancel
				return null;
			}
		}
		
		CMDHandler cmd = commands.get(name);
		if (cmd == null) {
			throw new MUMPSInterpretError(ctx, "Command not implemented: " + name);
		}
		
		// TODO: Appropriate place for a try/catch wrapper/error?
		return cmd.handle(this, ctx);
	}
	
	@Override
	public Object visitExprBinary(ExprBinaryContext ctx) {
		MVal.BinaryOp op = MVal.BINARY_OPS.get(ctx.OPER().getText());
		
		Object lhs = visit(ctx.expr(0));
		Object rhs = visit(ctx.expr(1));
		MVal val1 = MVal.valueOf(lhs);
		MVal val2 = MVal.valueOf(rhs);
		
		return val1.apply(op, val2);
	}
	
	@Override
	public Object visitExprUnary(ExprUnaryContext ctx) {
		MVal.UnaryOp op = MVal.UNARY_OPS.get(ctx.OPER().getText());
		MVal val = MVal.valueOf(visit(ctx.expr()));
		return val.apply(op);
	}
	
	@Override
	public Object visitExprGroup(ExprGroupContext ctx) {
		return visit(ctx.expr());
	}
	
	
	@Override
	public MVal visitExprLiteral(ExprLiteralContext ctx) {
		
		// for string literals, strip the surrounding "'s
		if (ctx.literal().STR_LITERAL() != null) {
			String str = ctx.literal().STR_LITERAL().getText();
			return MVal.valueOf(str.substring(1, str.length()-1));
		}
		return MVal.valueOf(ctx.getText());
	}
	
	@Override
	public Object visitExprVar(ExprVarContext ctx) {
		return visitVar(ctx.var());
	}
	
	@Override
	public Object visitVar(VarContext ctx) {
		// if flags starts with a ^ its a global, $ then its a special variable, otherwise its a local
		String flag = (ctx.flags == null) ? "" : ctx.flags.getText();
		String name = ctx.ID().getText();
		MVar ret = null;
		if (flag.equals("^")) {
			ret = proc.getGlobal(name);
		} else {
			ret = proc.getLocal(name);
		}
		
		// if its a subscripted global/var, resolve that as well
		if (ctx.args() != null && !ctx.args().isEmpty()) {
			for (ParseTree arg : ctx.args().children) {
				MVal obj = MVal.valueOf(visit(arg));
				ret = ret.get(obj.toString());
			}
		}
		return ret;
	}

	/*
	@Override
	public MVal visitExpr(ExprContext ctx) {
		List<Object> postfix = null;
		try {
			postfix = infixToPostfix(ctx.children);
		} catch (Exception ex) {
			throw new MUMPSInterpretError(ctx, "Error interpreting expression", ex, true);
		}
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
	
	*/
	
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
	/*

	@Override
	public Object visitRefRoutine(RefRoutineContext ctx) {
		// intrinsic system function, ID and args
		String name = ctx.ID().getText();
		
		RoutineProxy proxy = proc.getRoutine("$" + name + "^SYS");
		if (proxy == null) {
			throw new MUMPSInterpretError(ctx, "Unable to resolve: $" + name + " as a system function");
		}
		
		// special handling of args for $SELECT, $CASE, $TEXT, etc.
		if (Arrays.asList("S","SELECT","C","CASE","T","TEXT").contains(name)) {
			// TODO: Implement this
		}
		
		// resolve args (if any)
		Object[] args = new Object[0];
		if (ctx.args() != null) {
			args = new Object[ctx.args().arg().size()];
			for (int i=0; i < args.length; i++) {
				args[i] = visitArg(ctx.args(0).arg(i));
			}
		}
		
		try {
			Object ret = proxy.call("$"+name,proc, args);
			if (debug) {
				System.out.println("Output of $"+name+" is: " + ret);
			}
			return MVal.valueOf(ret);
		} catch (Exception e) {
			throw new MUMPSInterpretError(ctx, "Error calling: $"+name, e, true);
			
		}
		
	}
	
	@Override
	public Object visitRefRoutine(RefRoutineContext ctx) {
		String ep = ctx.ep.getText();
		String routine = (ctx.routine == null) ? "SYS" : ctx.routine.getText();
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
			List<MVal> args = resolveArgsToMVals(ctx.args(0));
			rvar.set(proxy.getName());
			return proxy.call(ep, proc, args.toArray(new Object[] {}));
		} catch (Exception e) {
			throw new MUMPSInterpretError(ctx, "Error invoking: " + ep + "^" + routine, e, true).setRoutine(oldVal);
		} finally {
			// restore old value
			if (oldVal != null) rvar.set(oldVal);
		}
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
			return visitExpr(ctx.expr().get(0));
		}
		throw new IllegalArgumentException("Unknown/unimplemented argument structure.");
	}
	*/
	
	// Evaluate methods -------------------------------------------------------
	
	public void setDebugMode(boolean value) {
		this.debug = value;
	}
	
	/** build and execute a lexer and parser and parse the stream */
	public static MUMPS2Parser parse(String stream) {
		//  build and execute a lexer and parser
		ANTLRInputStream input = new ANTLRInputStream(stream);
		MUMPS2Lexer lexer = new MUMPS2Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return new MUMPS2Parser(tokens);
	}
	
	/** build and execute a lexer and parser and parse the stream 
	 * @throws IOException */
	public static MUMPS2Parser parse(Reader reader) throws IOException {
		//  build and execute a lexer and parser
		ANTLRInputStream input = new ANTLRInputStream(reader);
		MUMPS2Lexer lexer = new MUMPS2Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return new MUMPS2Parser(tokens);
	}
	
	private static CommonTokenStream getTokenStream(String line) {
		ANTLRInputStream input = new ANTLRInputStream(line);
		MUMPS2Lexer lexer = new MUMPS2Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return tokens;
	}
	
	public Object evalLine(String line) {
		// parse the line and then evaluate it
		this.parser.reset();
		this.parser.setInputStream(getTokenStream(line));
		LineContext ctx = this.parser.line();
		
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
		for (LineContext line : filectx.line()) {
			if (exec) {

				visitLine(line);
				
			} else if (line.entryPoint() != null && line.entryPoint().ID().getText().equals(entrypoint)) {
				// this line is the entry point line matching the requested entrypoint, setup args, execute subsequent lines
				exec = true;
				
				// this is our target entry point, push the input variables onto the stack
				EntryPointArgsContext epargs = line.entryPoint().entryPointArgs();
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
//				if (line.cmdList() != null) {
//					ret = visitCmdList(rlc.cmdList());
//				}
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
			this.routineName = M4JInterpreter2.this.proc.getLocal("$ROUTINE").valStr();
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

	
	public static abstract class CMDHandler {
		private List<String> names;
		
		public CMDHandler(String... names) {
			this.names = Arrays.asList(names);
		}
		public List<String> getNames() {
			return this.names;
		}
		
		public Map<String,CMDHandler> getCollection() {
			Map<String,CMDHandler> ret = new HashMap<>();
			for (String name : getNames()) {
				ret.put(name.toUpperCase(), this);
			}
			return ret;
		}
		public abstract Object handle(M4JInterpreter2 interp, CmdContext ctx);
	}
	
	public static class SetCMDHandler extends CMDHandler {
		
		public SetCMDHandler() {
			super("S","SET");
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			for (ExprContext expr : ctx.expr()) {
				CMD_S(interp, expr);
			}
			return null;
		}
		
		/**
		 * CMD_S delegates to this, for reuse
		 * TODO: does not support the SET $E(X,*+n)="X" syntax
		 * TODO: does not support optional params of $P
		 */
		private Object CMD_S(M4JInterpreter2 interp, ExprContext expr) {
			// LHS of set can be 1) reference to local or global, 2) paren list of multiple values, 3) $P() function
			ParseTree lhs = expr.getChild(0);
			ParseTree oper = expr.getChild(1); // should be equals operator
			ParseTree rhs = expr.getChild(2);
			MVal val = (MVal) interp.visit(rhs);
			
			if (!oper.getText().equals("=")) {
				throw new IllegalArgumentException();
//				throw new MUMPSInterpretError(expr, "Expected a '=' operator for the SET command.");
			}
			
			// regular variable tager
			if (lhs instanceof ExprVarContext) {
				Object var = interp.visitVar(((ExprVarContext) lhs).var());
				if (var instanceof MVar) {
					((MVar) var).set(val.getOrigVal());
					return var;
				}
			}
			
			// if the set command target is $E or $P
			if (lhs instanceof ExprFuncContext) {
				/*
				if (ref.refFlags() != null && ref.refFlags().getText().equals("$") && 
						ref.ID(0).getText().toUpperCase().startsWith("E")) {
					// bypass function execution, just get the args
					MVar var = (MVar) interp.visitArg(ref.args(0).arg(0));
					MVal arg1 = MVal.valueOf(interp.visitArg(ref.args(0).arg(1)));
					MVal arg2 = (ref.args(0).arg().size() >= 3) ? MVal.valueOf(interp.visitArg(ref.args(0).arg(2))) : arg1;
					
					// replace the string with the new value
					StringBuffer sb = new StringBuffer(var.valStr());
					sb.replace(arg1.toNumber().intValue()-1, arg2.toNumber().intValue(), val.toString());
					
					// update the variable and return
					var.set(sb.toString());
					return var;
				} else if (ref.refFlags() != null && ref.refFlags().getText().equals("$") &&
						ref.ID(0).getText().toUpperCase().startsWith("P")) {
					// bypass executing the function, and get the args
					MVar var = (MVar) interp.visitArg(ref.args(0).arg(0));
					String delim = MVal.valueOf(interp.visitArg(ref.args(0).arg(1))).toString();
					Number from = (ref.args(0).arg().size() >= 3) ? MVal.valueOf(interp.visitArg(ref.args(0).arg(2))).toNumber() : 1;

					// do the string replacement, update the value and return
					var.set(MUMPS.$PIECE(var.valStr(), delim, from.intValue(), val.toString()));
					return var;
				}
				*/
			}
			throw new IllegalArgumentException();
//			throw new MUMPSInterpretError((ParserRuleContext) lhs, "Unknown target for SET command");
		}

	}
	
	public static class IfCMDHandler extends CMDHandler {
		public IfCMDHandler() {
			super("I","IF");
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			for (ExprContext expr : ctx.expr()) {
				
				// each expression should return boolean
				MVal ret = (MVal) interp.visit(expr);
				if (ret != null && !ret.isTruthy()) {
					return MFlowControl.FALSE;
				}
			}
			
			return MFlowControl.TRUE;
		}
	}
	
	public static class DoCMDHandler extends CMDHandler {
		public DoCMDHandler() {
			super("D","DO");
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			FileContext file = (FileContext) ctx.getParent().getParent();
			int curLine = ctx.getStart().getLine();
			if (file == null) return null; // from test cases/console?
			
			// use a special variable for now to track desired execution indent level
			int curIndent = MUMPS.$INCREMENT(interp.proc.getLocal("$INDENT")).intValue();
			
			for (LineContext line : file.line()) {
				// skip ahead to the next line after this DO command
				if (line.getStart().getLine() <= curLine) continue;
				
				// stop when the indent level is less than desired
				if (line.DOT().size() < curIndent) break;
				
				// execute the line
				Object ret = interp.visit(line);

				if (ret instanceof QuitReturn) {
					break;
				}
			}
			
			// decrement the indent level
			MUMPS.$INCREMENT(interp.proc.getLocal("$INDENT"),-1);
			
			return null;
		}
		
	}
	
	public static class WriteCMDHandler extends CMDHandler {

		public WriteCMDHandler() {
			super("W", "WRITE");
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// if no expressions, list all the local vars
			if (ctx.expr().isEmpty()) {
				Iterator<String> itr = interp.proc.listLocals();
				while (itr.hasNext()) {
					interp.proc.getOutputStream().println(itr.next());
				}
				return null;
			}
			
			// loop through each expression, write to output stream 
			for (ExprContext expr : ctx.expr()) {
				Object obj = interp.visit(expr);
				if (obj == null) continue;
				
				// TODO: handle !!
				// TODO: Handle ?45 for indenting to position 45.
				if (obj.equals("!")) interp.proc.getOutputStream().println();
				else interp.proc.getOutputStream().print(obj);
			}
			
			return null;
		}
	}

}