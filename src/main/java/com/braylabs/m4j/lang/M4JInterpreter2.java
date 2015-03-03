package com.braylabs.m4j.lang;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MUMPS2Parser.ArgsContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdPostCondContext;
import com.braylabs.m4j.lang.MUMPS2Parser.EntryPointArgsContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprBinaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprFormatContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprFuncContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprGroupContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprIndrExprContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprIndrRefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprIndrVarContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLineLabel2Context;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLineLabelContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLiteralContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprMatchContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprPatternItemContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprRefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprUnaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprVarContext;
import com.braylabs.m4j.lang.MUMPS2Parser.FileContext;
import com.braylabs.m4j.lang.MUMPS2Parser.FuncContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LineContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LinesContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LiteralContext;
import com.braylabs.m4j.lang.MUMPS2Parser.RefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.VarContext;
import com.braylabs.m4j.lang.MVal.BinaryOp;
import com.braylabs.m4j.parser.MInterpreter.MFlowControl;
import com.braylabs.m4j.parser.MInterpreter.QuitReturn;
import com.braylabs.m4j.parser.MInterpreter.UnderlineErrorListener;
import com.sun.org.apache.xml.internal.utils.UnImplNode;

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
		commands.putAll(new QuitCMDHandler().getCollection());
		commands.putAll(new NewCMDHandler().getCollection());
		commands.putAll(new $TCMDHandler().getCollection());
		commands.putAll(new $SCMDHandler().getCollection());
	}
	
	@Override
	public Object visitLines(LinesContext ctx) {
		Object ret = null;
		for (LineContext line : ctx.line()) {
			ret = visitLine(line);
		}
		return ret;
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
		
		// WRITE expression such as W !?10,"HI" will invoke UnaryExpr but ! is a Binary operator
		// so this is a hack to get it to work.
		if (op == null && ctx.OPER().getText().equals("!")) {
			return MVal.valueOf("\n").apply(BinaryOp.CONCAT, val);
		}
		
		
		return val.apply(op);
	}
	
	@Override
	public Object visitExprGroup(ExprGroupContext ctx) {
		return visit(ctx.expr());
	}
	
	
	@Override
	public MVal visitExprLiteral(ExprLiteralContext ctx) {
		LiteralContext literal = ctx.literal();
		
		// for string literals, strip the surrounding "'s
		if (literal.STR_LITERAL() != null) {
			String str = literal.STR_LITERAL().getText();
			return MVal.valueOf(str.substring(1, str.length()-1));
		}
		return MVal.valueOf(literal.getText());
	}

	@Override
	public String visitExprFormat(ExprFormatContext ctx) {
		StringBuffer sb = new StringBuffer();
		if (ctx.format().PAT_INT() != null) {
			// column position
			int count = Integer.parseInt(ctx.format().PAT_INT().getText());
			for (int i=0; i < count; i++) sb.append(" ");
		}
		if (ctx.format().OPER() != null) {
			for (int i=0; i < ctx.format().OPER().size(); i++) {
				String op = ctx.format().OPER(i).getText();
				if (op.equals("!")) {
					// new line
					sb.append("\n");
				} else if (op.equals("#")) {
					// form feed
					sb.append("\r\f");
				}
			}
		}
		
		return sb.toString();
	}
	
	@Override
	public Object visitExprFunc(ExprFuncContext ctx) {
		return visitFunc(ctx.func());
	}
	
	@Override
	public MVar visitExprVar(ExprVarContext ctx) {
		return visitVar(ctx.var());
	}
	
	@Override
	public MVal visitExprIndrRef(ExprIndrRefContext ctx) {
		// resolve args
		MVal[] args = visitArgs(ctx.args());

		// resolve ref, then resolve it as a var
		Object ret = visitRef(ctx.ref());
		MVar var = resolveVar(ret.toString(), args);
		return MVal.valueOf(var);
	}
	
	@Override
	public MVal visitExprIndrVar(ExprIndrVarContext ctx) {
		// resolve args
		MVal[] args = visitArgs(ctx.args());
		
		// double resolve the variable
		MVar var = visitVar(ctx.var());
		var = resolveVar(var.valStr(), args);
		return MVal.valueOf(var);
	}
	
	@Override
	public MVal visitExprIndrExpr(ExprIndrExprContext ctx) {
		System.out.println("VISITEXPTRINDREXPR");
		return null;
	}
		
	@Override
	public Object visitExprMatch(ExprMatchContext ctx) {
		// first evaluate the left side into a string
		MVal val = MVal.valueOf(visit(ctx.expr()));
		
		// loop through each pattern, see if it matches
		int offset = 0;
		for (ExprPatternItemContext pat : ctx.exprPatternItem()) {
			int repeat = (pat.PAT_INT() == null) ? 1 : Integer.parseInt(pat.PAT_INT().getText());
			String codes = (pat.PAT_CODES() == null) ? null : pat.PAT_CODES().getText();
			String literal = (pat.PAT_LITERAL() == null) ? null : pat.PAT_LITERAL().getText();

			boolean match = false;
			if (literal != null) {
				// strip quotes
				literal = literal.substring(1, literal.length()-1);
				match = val.matches(offset, repeat, literal);
				offset += literal.length() * repeat;
			} else if (codes != null) {
				match = val.matches(offset, repeat, codes.toCharArray());
				offset += repeat;
			}
			
			if (!match) return MVal.FALSE;
		}
		
		return MVal.TRUE;
	}
	
	@Override
	public Object visitFunc(FuncContext ctx) {
		String name = ctx.name.getText();

		// system functions live in the ^SYS routine or in the list of commands
		CMDHandler handler = commands.get("$" + name.toUpperCase());
		RoutineProxy proxy = proc.getRoutine("$" + name + "^SYS");
		if (handler != null) {
			// let the handler handle it
			return handler.handle(this, ctx);
		} else if (proxy == null) {
			throw new MUMPSInterpretError(ctx, "Unable to resolve: $" + name + " as a system function");
		}
		
		// resolve args (if any)
		Object[] args = new Object[0];
		if (ctx.args() != null) {
			args = new Object[ctx.args().expr().size()];
			for (int i=0; i < args.length; i++) {
				args[i] = visit(ctx.args().expr(i));
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
	public MVar visitVar(VarContext ctx) {
		// if flags starts with a ^ its a global, $ then its a special variable, otherwise its a local
		// if name == null, then its a naked global reference
		String flag = (ctx.flags == null) ? "" : ctx.flags.getText();
		String name = (ctx.ID() == null) ? null : ctx.ID().getText();
		
		// track or use the last used global (for naked global reference)
		if (flag.equals("^") && name != null) {
			proc.getSpecialVar("$M4JLASTGLOBAL").set(name);
		} else if (flag.equals("^") && name == null) {
			name = proc.getSpecialVar("$M4JLASTGLOBAL").valStr();
		}
		
		// if its a subscripted global/var, resolve that as well
		MVal[] args = visitArgs(ctx.args());
		return resolveVar(flag + name, args);
	}
	
	/** Helper function to resolve variables */
	public MVar resolveVar(String name, MVal[] args) {
		MVar ret = null;
		if (name.startsWith("^")) {
			ret = proc.getGlobal(name);
		} else {
			ret = proc.getLocal(name);
		}
		
		// if its a subscripted global/var, resolve that as well
		if (args != null) {
			for (MVal arg : args) {
				ret = ret.get(arg.toString());
			}
		}
		return ret;
	}
	
	@Override
	public Object visitRef(RefContext ctx) {
		String ep = ctx.ep.getText();
		String routine = (ctx.routine == null) ? "SYS" : ctx.routine.getText();
		MVar curRoutine = proc.getLocal("$ROUTINE");
			
		RoutineProxy proxy = proc.getRoutine(routine);
		if (proxy == null) {
			throw new IllegalArgumentException("Routine is undefined: " + routine);
		}
			
		// track currently executing routine as special var for now
		// then invoke routine, return result
		MVar rvar = proc.getLocal("$ROUTINE");
		String oldVal = rvar.valStr();
		try {
			
			MVal[] args = (ctx.args() == null) ? null : new MVal[ctx.args().expr().size()];
			for (int i=0; i < args.length; i++) {
				args[i] = MVal.valueOf(visit(ctx.args().expr(i)));
			}

			rvar.set(proxy.getName());
			return proxy.call(ep, proc, args);
		} catch (Exception e) {
			throw new MUMPSInterpretError(ctx, "Error invoking: " + ep + "^" + routine, e, true).setRoutine(oldVal);
		} finally {
			// restore old value
			if (oldVal != null) rvar.set(oldVal);
		}
	}
	
	@Override
	public MVal[] visitArgs(ArgsContext ctx) {
		if (ctx == null) return null;
		
		MVal[] ret = new MVal[ctx.expr().size()];
		for (int i=0; i < ctx.expr().size(); i++) {
			ret[i] = MVal.valueOf(visit(ctx.expr(i)));
		}
		
		return ret;
	}
	
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
		LinesContext ctx = this.parser.lines();
		
		// print debug info if set
		if (this.debug) {
			System.out.println("Evaluating LINE: " + line);
			System.out.println("TREE: " + ctx.toStringTree(parser));
		}
		
		// actually perform the evaluation (unless syntax parse errors)
		if (this.parser.getNumberOfSyntaxErrors() > 0) {
			throw new MUMPSInterpretError(ctx, "Parse Errors: " + this.parser.getNumberOfSyntaxErrors(), null, true);
		}
		
		try {
			return visitLines(ctx);
		} catch (MUMPSInterpretError err) {
			handleError(err);
		}
		return MFlowControl.ERROR;
	}
	
	protected void handleError(MUMPSInterpretError err) {
		// TODO: Implement this
		throw err;
	}
	
	protected void throwError(ParserRuleContext ctx, String msg) {
		throw new MUMPSInterpretError(ctx, msg);
	}
	
	protected void throwError(ParserRuleContext ctx, String msg, Throwable t) {
		throw new MUMPSInterpretError(ctx, msg, t, false);
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
		public Object handle(M4JInterpreter2 interp, FuncContext ctx) {
			throw new UnsupportedOperationException("Function not implemented");
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
				FuncContext funcctx = ((ExprFuncContext) lhs).func();
				String name = funcctx.name.getText().toUpperCase();
				
				if (name.equals("E") || name.equals("EXTRACT")) {
					MVar var = (MVar) interp.visit(funcctx.args().expr(0));
					MVal arg1 = MVal.valueOf(interp.visit(funcctx.args().expr(1)));
					MVal arg2 = (funcctx.args().expr().size() >= 3) ? MVal.valueOf(interp.visit(funcctx.args().expr(2))) : arg1;
					
					// replace the string with the new value
					StringBuffer sb = new StringBuffer(var.valStr());
					sb.replace(arg1.toNumber().intValue()-1, arg2.toNumber().intValue(), val.toString());
					
					// update the variable and return
					var.set(sb.toString());
					return var;
				} else if (name.equals("P") || name.equals("PIECE") && funcctx.args().expr(0) instanceof ExprVarContext) {
					// bypass executing the function, and get the args
					VarContext varctx = ((ExprVarContext) funcctx.args().expr(0)).var();
					MVar var = interp.resolveVar(varctx.ID().getText(), null);
					String delim = MVal.valueOf(interp.visit(funcctx.args().expr(1))).toString();
					Number from = (funcctx.args().expr().size() >= 3) ? MVal.valueOf(interp.visit(funcctx.args().expr(2))).toNumber() : 1;

					// do the string replacement, update the value and return
					var.set(MUMPS.$PIECE(var.valStr(), delim, from.intValue(), val.toString()));
					return var;

				}
				
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
			Object lineOrFileCtx = ctx.getParent().getParent();
			int curLine = ctx.getStart().getLine();
			if (lineOrFileCtx == null || !(lineOrFileCtx instanceof FileContext)) return null; // from test cases/console?
			
			// use a special variable for now to track desired execution indent level
			int curIndent = MUMPS.$INCREMENT(interp.proc.getLocal("$INDENT")).intValue();
			
			for (LineContext line : ((FileContext) lineOrFileCtx).line()) {
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
				
				if (obj instanceof MVar) {
					interp.proc.getOutputStream().print(((MVar) obj).valStr());
				} else {
					interp.proc.getOutputStream().print(obj);
				}
			}
			
			return null;
		}
	}
	
	public static class QuitCMDHandler extends CMDHandler {
		
		public QuitCMDHandler() {
			super("Q","QUIT");
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// return the value of the first expression
			Object ret = null;
			if (ctx.expr() != null) {
				ret = interp.visit(ctx.expr(0));
				
				// wrap return value in marker class
				ret = new QuitReturn(ret);
			} else {
				// Q w/o any expressions usually indicates quit loop, etc.
				return MFlowControl.QUIT;
			}
			
			return ret;
		}
	}
	
	public static class NewCMDHandler extends CMDHandler {

		public NewCMDHandler() {
			super("N","NEW");
		}
		
		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// collect variable name(s)
			List<String> vars = new ArrayList<>();
			for (ExprContext expr : ctx.expr()) {
				// should contain a ref, but treat it as a literal value not a variable reference
				vars.add(expr.getChild(0).getText());
			}
			
			// newed all them vars!
			interp.proc.push(false, vars.toArray(new String[]{}));
			return null;
		}
	}
	
	public static class $TCMDHandler extends CMDHandler {
		
		public $TCMDHandler() {
			super("$T", "$TEXT");
		}
		
		@Override
		public Object handle(M4JInterpreter2 interp, FuncContext ctx) {
			// inspect the arguments instead of evaluating them
			
			// only expect one expr of type ExprLineLabel
			if (ctx.args() == null || ctx.args().expr().size() != 1) {
				interp.throwError(ctx, "<SYNTAX> Unknown sytax of $TEXT");
			}

			// there are several structures that could appear in the arguments
			String tag = null, routine = null;
			int offset = 0;
			ExprContext expr = ctx.args().expr(0);
			if (expr instanceof ExprLineLabelContext) {
				// "TAG+N^ROUTINE" style
				ExprLineLabelContext label = (ExprLineLabelContext) ctx.args().expr(0);
				tag = (label.tag == null) ? null : label.tag.getText();
				routine = (label.routine == null) ? null : label.routine.getText();
				offset = (label.n == null) ? 0 : Integer.parseInt(label.n.getText());
			} else if (expr instanceof ExprRefContext) {
				// "TAG^ROUTINE" style
				RefContext ref = ((ExprRefContext) expr).ref(); 
				tag = ref.ep.getText();
				routine = ref.routine.getText();
			} else if (expr instanceof ExprLineLabel2Context) {
				ExprLineLabel2Context lineexpr = (ExprLineLabel2Context) expr;
				tag = lineexpr.tag.getText();
				routine = (lineexpr.routine == null) ? null : lineexpr.routine.getText();
				offset = MVal.valueOf(interp.visit(lineexpr.expr())).toNumber().intValue();
			}
			
			RoutineProxy proxy = interp.proc.getRoutine(routine);
			String line = null;
			try {
				line = proxy.getRoutineLine(tag, offset);
			} catch (IOException e) {
				interp.throwError(ctx, "Error while looking up source line", e);
			}
			
			return line;
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			return null;
		}
	}
	
	public static class $SCMDHandler extends CMDHandler {
		public $SCMDHandler() {
			super("$S", "$SELECT");
		}
		
		@Override
		public Object handle(M4JInterpreter2 interp, FuncContext ctx) {
			ArgsContext args = ctx.args();
			for (int i=0; i < args.children.size(); i++) {
				// expects expr:val combos separated by commas
				MVal expr = MVal.valueOf(interp.visit(args.getChild(i)));
				String delim = args.getChild(++i).getText();
				if (!delim.equals(":")) {
					interp.throwError(ctx, "<SYNTAX> Unrecognized format of $SELECT function");
				} else if (expr.isTruthy()) {
					// this is our match, evaluate the next token as the value
					return MVal.valueOf(interp.visit(args.getChild(++i)));
				} else {
					// ignore the next value (indicates lazy evaluation)
					i++;
				}
				
				// if next token is a comma, consume it and keep going
				if (++i < args.children.size()) {
					delim = args.getChild(i).getText();
				}
			}
			
			return super.handle(interp, ctx);
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// TODO Auto-generated method stub
			return null;
		}
	}
	

}