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
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLineRefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprListContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLiteralContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprMatchContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprPatternItemContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprRefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprUnaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprVarContext;
import com.braylabs.m4j.lang.MUMPS2Parser.FileContext;
import com.braylabs.m4j.lang.MUMPS2Parser.FuncContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LineContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LineRefContext;
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
		commands.putAll(new ForCMDHandler().getCollection());
		commands.putAll(new HaltHangCMDHandler().getCollection());
		commands.putAll(new GoCMDHandler().getCollection());
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

		// otherwise process command list and return the value of last command executed
		return processCmds(ctx.cmd());
	}

	@Override
	public Object visitCmd(CmdContext ctx) {
		// ensure that this command is defined/implemented
		String name = ctx.ID().getText().toUpperCase();
		
		// if there is a PCE, evaluate it.  Skip the command execution if its false.
		if (!visitCmdPostCond(ctx.cmdPostCond())) {
			return null;
		}
		
		CMDHandler cmd = commands.get(name);
		if (cmd == null) {
			throw new MUMPSInterpretError(ctx, "Command not implemented: " + name);
		}
		
		try {
			return cmd.handle(this, ctx);
		} catch (Throwable t) {
			throw throwError(ctx, "Error processing command: " + name, t, true);
		}
	}

	/** returns the result of evaluating the post conditional expression, true if its null */
	@Override
	public Boolean visitCmdPostCond(CmdPostCondContext pce) {
		if (pce == null) return true; // doesn't exist, so return true
		MVal ret = MVal.valueOf(visit(pce.expr()));
		if (ret != null && !ret.isTruthy()) {
			return false;
		}
		return true;
	}
	
	/* Consolidated logic for processing and aborting a list of commands */
	private Object processCmds(List<CmdContext> cmds) {
		if (cmds == null) return null;
		Object ret = null;
		for (CmdContext cmd : cmds) {
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
		return ret;
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
		// TODO: Is this still necessary? Might be handled better now by visitExprFormat()?
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
	public Object visitExprRef(ExprRefContext ctx) {
		return visitRef(ctx.ref());
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
		// TODO: implement me
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
	public Object visitExprLineRef(ExprLineRefContext ctx) {
		if (!visitCmdPostCond(ctx.cmdPostCond())) {
			return null;
		}
		return visitLineRef(ctx.lineRef());
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
		// TODO: Convert this to a call to vistArgs()?
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
	public ResolvedLineRef visitLineRef(LineRefContext ctx) {
		ResolvedLineRef ret = new ResolvedLineRef();
		
		// "TAG+N^ROUTINE" style
		ret.tag = (ctx.tag == null) ? null : ctx.tag.getText();
		ret.routine = (ctx.routine == null) ? null : ctx.routine.getText();
		ret.offset = (ctx.n == null) ? 0 : Integer.parseInt(ctx.n.getText());
		
		// offset could be an expression instead of n
		if (ctx.n == null && ctx.expr() != null) {
			ret.offset = MVal.valueOf(visit(ctx.expr())).toNumber().intValue();
		}
		
		// missing routine indicates current routine
		if (ret.routine == null) {
			MVar rvar = proc.getLocal("$ROUTINE");
			ret.routine = rvar.valStr();
			if (ret.routine == null) {
				throw throwError(ctx, "Routine name not specified and no current routine context exists to fall back on");
			}
		}
		
		return ret;
	}
	
	/** TODO: not sure what I think about this technique, similar to QuitReturn, but feels weird */
	static class ResolvedLineRef {
		String tag = null;
		String routine = null;
		int offset = -1;
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (tag != null) { 
				sb.append(tag); 
				if (offset > 0) {
					sb.append("+");
					sb.append(offset);
				}
				sb.append("^");
			}
			sb.append(routine);
			return sb.toString();
		}
	}
	
	@Override
	public Object visitRef(RefContext ctx) {
		String ep = ctx.ep.getText();
		String routine = (ctx.routine == null) ? "SYS" : ctx.routine.getText();
		return invokeRoutine(ctx, routine, ep, 0);
	}
	
	/** shared code to invoke routine common to GOTO, DO and RefContext */
	private Object invokeRoutine(ParserRuleContext ctx, String routine, String tag, int offset) {
		MVar rvar = proc.getLocal("$ROUTINE");
		RoutineProxy proxy = proc.getRoutine(routine);
		if (proxy == null) {
			throw new MUMPSInterpretError(ctx, "Routine is undefined: " + routine);
		}
			
		// track currently executing routine as special var for now
		// then invoke routine, return result
		String oldVal = rvar.valStr();
		try {
			MVal[] args = visitArgs(ctx.getChild(ArgsContext.class, 0));
			rvar.set(proxy.getName());
			return proxy.call(tag, proc, args);
		} catch (Exception e) {
			throw new MUMPSInterpretError(ctx, "Error invoking: " + tag + "^" + routine, e, true).setRoutine(oldVal);
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
				ret = processCmds(line.cmd());
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

	protected void handleError(MUMPSInterpretError err) {
		// TODO: Implement this
		throw err;
	}
	
	protected MUMPSInterpretError throwError(ParserRuleContext ctx, String msg) {
		MUMPSInterpretError ret = new MUMPSInterpretError(ctx, msg);
		throw ret;
	}
	
	protected MUMPSInterpretError throwError(ParserRuleContext ctx, String msg, Throwable t) {
		MUMPSInterpretError ret = new MUMPSInterpretError(ctx, msg, t, false);
		throw ret;
	}

	protected MUMPSInterpretError throwError(ParserRuleContext ctx, String msg, Throwable t, boolean suppress) {
		MUMPSInterpretError ret = new MUMPSInterpretError(ctx, msg, t, suppress);
		throw ret;
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
		 */
		private static Object CMD_S(M4JInterpreter2 interp, ExprContext expr) {
			// LHS of set can be 1) reference to local or global, 2) paren list of multiple values, 3) $P() function
			ParseTree lhs = expr.getChild(0);
			ParseTree oper = expr.getChild(1); // should be equals operator
			ParseTree rhs = expr.getChild(2);
			MVal val = (MVal) interp.visit(rhs);
			
			if (!oper.getText().equals("=")) {
				interp.throwError(expr, "Expected a '=' operator for the SET command.");
				return null;
			}
			
			// regular variable target
			if (lhs instanceof ExprVarContext) {
				MVar var = interp.visitVar(((ExprVarContext) lhs).var());
				var.set(val.getOrigVal());
				return var;
			}
			
			// variable list target S (A,B,C)=1
			// TODO: Refactor this? it seems a little redundant
			if (lhs instanceof ExprListContext) {
				for (ExprContext var : ((ExprListContext) lhs).expr()) {
					if (var instanceof ExprVarContext) {
						MVar ret = interp.visitVar(((ExprVarContext) var).var());
						ret.set(val.getOrigVal());
					} else if (var instanceof ExprFuncContext) {
						handleSetFunc(interp, ((ExprFuncContext) var).func(), val);			
					} else {
						// invalid, break loop causes error
						throw interp.throwError((ParserRuleContext) lhs, "Unknown target for SET list command");
					}
				}
				// success, return
				return null;
			}
			
			// if the set command target is $E or $P
			if (lhs instanceof ExprFuncContext) {
				return handleSetFunc(interp, ((ExprFuncContext) lhs).func(), val);			
			}
			
			throw interp.throwError((ParserRuleContext) lhs, "Unknown target for SET command");
		}
		
		/** delegates to handling the $P and $E set syntax */
		private static MVar handleSetFunc(M4JInterpreter2 interp, FuncContext funcctx, MVal val) {
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
			throw interp.throwError(funcctx, "Unrecognized function target for SET command: " + name);
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
	
	/**
	 * TODO: Argumentless GOTO command not implemented
	 * TODO: You can specify a $CASE function as a GOTO command argument.
	 * TODO: Lots of context switching issues need to be addressed
	 * TODO: GOTO within loop?
	 * TODO: Not sure what the behavior should be for multiple goarguments should be? (run them all? just the first?)
	 */
	public static class GoCMDHandler extends CMDHandler {
		public GoCMDHandler() {
			super("G", "GOTO");
		}
		
		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			for (ExprContext expr : ctx.expr()) {
				// goto the first one that is true or lacks a PCE
				if (expr instanceof ExprLineRefContext) {
					LineRefContext lineref = ((ExprLineRefContext) expr).lineRef();
					ResolvedLineRef ref = interp.visitLineRef(lineref);
					return interp.invokeRoutine(expr, ref.routine, ref.tag, ref.offset);
				}
			}
			throw interp.throwError(ctx, "Unrecognized structure of GOTO command");
		}
	}
	
	public static class DoCMDHandler extends CMDHandler {
		public DoCMDHandler() {
			super("D","DO");
		}

		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// if we have parameters, then its an invoke routine style of do
			if (ctx.expr() != null) {
				Object ret = null;
				for (ExprContext expr : ctx.expr()) {
					ret = interp.visit(expr);
					// TODO: evaluate an optional postconditional
				}
				
				return ret;
			}
			
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
					MVar var = interp.proc.getLocal(itr.next());
					interp.proc.getOutputStream().println(var);
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
			if (!ctx.expr().isEmpty()) {
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
			ResolvedLineRef ref = null;
			ExprContext expr = ctx.args().expr(0);
			if (expr instanceof ExprLineRefContext) {
				// "TAG+N^ROUTINE" style
				LineRefContext label = ((ExprLineRefContext) ctx.args().expr(0)).lineRef();
				ref = interp.visitLineRef(label);
			} else if (expr instanceof ExprRefContext) {
				// "TAG^ROUTINE" style
				RefContext exprref = ((ExprRefContext) expr).ref(); 
				ref = new ResolvedLineRef();
				ref.offset=0;
				ref.tag = exprref.ep.getText();
				ref.routine = exprref.routine.getText();
			}
			
			RoutineProxy proxy = interp.proc.getRoutine(ref.routine);
			String line = null;
			try {
				line = proxy.getRoutineLine(ref.tag, ref.offset);
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
	
	public static class HaltHangCMDHandler extends CMDHandler {
		public HaltHangCMDHandler() {
			super("H","HALT","HANG");
		}
		
		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// resolve the ambigious command
			String cmdname = ctx.ID().getText().toUpperCase();
			
			// HALT or H w/o any arguments is a HALT
			if (cmdname.equals("HALT")) {
				return MFlowControl.HALT;
			} else if (cmdname.equals("H") && ctx.expr().isEmpty()) {
				return MFlowControl.HALT;
			}
			
			// it should be a HANG now, with 1+ second durations
			for (ExprContext expr : ctx.expr()) {
				MVal val = MVal.valueOf(interp.visit(expr));
				float sleepSecs = ((MVal) val).toNumber().floatValue();
				hang(sleepSecs);
			}
			return null;
		}
		
		private void hang(float secs) {
			try {
				Thread.sleep(Math.round(secs * 1000));
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}
	
	public static class ForCMDHandler extends CMDHandler {
		public ForCMDHandler() {
			super("F", "FOR");
		}
		
		@Override
		public Object handle(M4JInterpreter2 interp, CmdContext ctx) {
			// F i=1:1:10 style expression
			ArgsContext args = ctx.args();
			int size = args.children.size();
			if (size < 3 || !args.getChild(1).getText().equals(":")) {
				throw interp.throwError(ctx,"FOR command expected 3+ tokens in command list");
			}
			
			// execute the first expression as an assignment operator, get the loop variable reference
			MVar loopVar = (MVar) SetCMDHandler.CMD_S(interp, args.expr(0));
			MVal inc = MVal.valueOf(interp.visit(args.expr(1)));
			MVal limit = (args.children.size() >= 5) ? MVal.valueOf(interp.visit(args.expr(2))) : null;
			boolean countDown = inc.apply(BinaryOp.LTE, MVal.valueOf(-1)).isTruthy();

			// get the parent line and determine the location of this for command
			int cmdIdx = -1;
			List<CmdContext> cmds = ((LineContext) ctx.getParent()).cmd();
			for (int i=0; i < cmds.size(); i++) {
				if (cmds.get(i) == ctx) {
					cmdIdx = i; break;
				}
			}
			if (cmdIdx < 0) throw new IllegalArgumentException("Unable to determine where the FOR command is in the list");

			// TODO: need a pre-check?  this will always run once.
			for(;;) {
				Object ret = null;
				// execute subsequent commands on line
				for (int i=cmdIdx+1; i < cmds.size(); i++) {
					ret = interp.visitCmd(cmds.get(i));
					
					// TODO: Redundant with M4JInterpreter.processCmds()
					if (ret == null) {
						// ???
					} else if (ret == MFlowControl.QUIT || ret instanceof QuitReturn) {
						// quit within loop indicate terminate loop
						break;
					} else if (ret == MFlowControl.FALSE) {
						// returned false, stop processing this line, but continue loop
						break;
					}
				}
				
				if (ret == MFlowControl.QUIT || ret instanceof QuitReturn) {
					break;
				}

				// do the increment
				MUMPS.$INCREMENT(loopVar, inc);
				
				// if the increment is equal to the limit, break
				if (countDown && limit != null && MVal.valueOf(loopVar).apply(BinaryOp.LT, limit).isTruthy()) {
					break;
				} else if (!countDown && limit != null && MVal.valueOf(loopVar).apply(BinaryOp.GT, limit).isTruthy()) {
					break;
				}
			}

			// loop has been completely executed, stop processing further commands on line (like IF statement)
			return MFlowControl.FALSE;
		}
	}

}