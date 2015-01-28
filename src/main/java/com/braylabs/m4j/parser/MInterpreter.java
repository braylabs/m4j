package com.braylabs.m4j.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MVal;
import com.braylabs.m4j.lang.MVal.BinaryOp;
import com.braylabs.m4j.parser.MCmd.MCmdI;
import com.braylabs.m4j.parser.MCmd.MCmdQ;
import com.braylabs.m4j.parser.MUMPSParser.ArgContext;
import com.braylabs.m4j.parser.MUMPSParser.AssignExprContext;
import com.braylabs.m4j.parser.MUMPSParser.BinaryOpExprContext;
import com.braylabs.m4j.parser.MUMPSParser.CmdContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprContext;
import com.braylabs.m4j.parser.MUMPSParser.LineContext;
import com.braylabs.m4j.parser.MUMPSParser.LiteralContext;
import com.braylabs.m4j.parser.MUMPSParser.PceContext;
import com.braylabs.m4j.parser.MUMPSParser.RefContext;
import com.braylabs.m4j.parser.MUMPSParser.RegularLineContext;

public class MInterpreter extends MUMPSBaseVisitor<Object> {

	private M4JProcess proc;
	private boolean debug;

	public MInterpreter(M4JProcess proc) {
		this.proc = proc;
	}
	
	@Override
	public Object visitRegularLine(RegularLineContext ctx) {
		
		// loop through each command and evaluate it
		for (CmdContext cmd : ctx.cmdList().cmd()) {
			
			// if its a quit command and returns, stop evaluating
			Object ret = visit(cmd);
			if (ret == null) {
				// ???
			} else if (ret instanceof MCmdQ.QuitReturn) {
				// quit and return;
			} else if (ret == MCmdI.FALSE) {
				// returned false, stop processing this line
				break;
			}
		}
		
		return null;
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
			Object ret = visit(pc);
			if (ret != null && ret == Boolean.FALSE) {
				// cancel
				return null;
			}
		}
		
		switch (name) {
			case "W":
			case "WRITE":
				return CMD_W(ctx);
			case "S":
			case "SET":
				return CMD_S(ctx);
			case "I":
			case "IF":
				return CMD_I(ctx);
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
	
	private Object CMD_I(CmdContext ctx) {
		for (ExprContext expr : ctx.exprList().expr()) {
			
			// each expression should return boolean
			Object ret = visit(expr);
			if (ret != null && ret == Boolean.FALSE) {
				return MCmdI.FALSE;
			}
		}
		
		return MCmdI.TRUE;
	}
	
	@Override
	public Object visitAssignExpr(AssignExprContext ctx) {
		System.out.println("visitAssignExpr");
		return null;
	}
	
	@Override
	public Object visitBinaryOpExpr(BinaryOpExprContext ctx) {
		
		
		List<Object> postfix = infixToPostfix(ctx.children);
		
		// evaluate stack, stop when there is only 1 remaining item and return it
		for (int i=0; i < postfix.size() && postfix.size() > 1; i++) {
			// if this item is not an operator, keep going
			Object op = postfix.get(i);
			if (!(op instanceof BinaryOp)) {
				continue;
			} else if (postfix.size() < 3) {
				throw new IllegalStateException("Expected at least 2 operands to still be on stack");
			}
			
			// we have an operator, remove it and the LHS and RHS
			postfix.remove(i--);
			
			// convert/visit the lhs/rhs if needed
			Object rhs = postfix.remove(i--);
			Object lhs = postfix.remove(i--);
			MVal val1 = MVal.valueOf((lhs instanceof ParseTree) ? visit((ParseTree) lhs) : lhs);
			MVal val2 = MVal.valueOf((rhs instanceof ParseTree) ? visit((ParseTree) rhs) : rhs);
			
			// evaluate and push back on stack
			postfix.add(i+1, val1.apply((BinaryOp) op, val2));
			
		}
		
		return postfix.get(0);
	}

	public List<Object> infixToPostfix(List<ParseTree> items) {
		List<Object> ret = new ArrayList<>();
		Stack<BinaryOp> ops = new Stack<>();
		
		for (ParseTree item : items) {
			if (item instanceof TerminalNode && MVal.BINARY_OPS.containsKey(item.getText())) {
				// push operator on stack
				ops.push(MVal.BINARY_OPS.get(item.getText()));
			} else {
				ret.add(visit(item));
			}
		}
		
		// empty stack
		while (!ops.empty()) ret.add(ops.pop());
		
		return ret;
	}

	
	private Object CMD_W(CmdContext ctx) {
		// loop through each expression, write to output stream 
		for (ExprContext expr : ctx.exprList().expr()) {
			Object obj = visit(expr);
			
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
				MVar var = visitRef((RefContext) lhs);

				// set the value
				Object val = visit(rhs);
				var.set(val);
			} else {
				throw new RuntimeException();
			}
		}
		return null;
	}
	
	@Override
	public MVar visitRef(RefContext ctx) {
		MVar ret = proc.getLocal(ctx.ID(0).getText());
		
		if (ctx.args() != null) {
			for (ArgContext arg : ctx.args().arg()) {
				ret = ret.get(visitArg(arg));
			}
		}
		
		return ret;
	}
	
	@Override
	public String visitArg(ArgContext ctx) {
		// strip the "'s off string literals
		if (ctx.STR_LITERAL() != null) {
			String ret = ctx.STR_LITERAL().getText();
			return ret.substring(1, ret.length()-1);
		}
		return ctx.toString();
	}
	
	// Evaluate methods -------------------------------------------------------
	
	public void setDebugMode(boolean value) {
		this.debug = value;
	}
	
	/** build and execute a lexer and parser and parse the stream */
	private static MUMPSParser parse(String stream) {
		//  build and execute a lexer and parser
		ANTLRInputStream input = new ANTLRInputStream(stream);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return new MUMPSParser(tokens);
	}
	
	public Object evalLine(String line) {
		// parse the line and then evaluate it
		MUMPSParser parser = parse(line);
		LineContext ctx = parser.line();
		
		// print debug info if set
		if (this.debug) {
			System.out.println("Evaluating LINE: " + line);
			System.out.println("TREE: " + ctx.toStringTree(parser));
		}
		
		// actually perform the evaluation
		return visit(ctx);
	}
	
	public static void main(String[] args) {
		String m = " S FOO(\"bar\")=\"hello\",FOO(\"baz\")=\"world!\" I FOO(\"bar\")=\"hello\" W !,FOO(\"bar\")_\" \"_FOO(\"baz\"),! ; should write hello world";
//		m = " S FOO=\"Hell\",BAR=\"Wor\" W !,FOO_\" \"_BAR,1+1\n";

		// create a new process and interpreter
		M4JProcess proc = new M4JProcess(null, 1);
		proc.setOutputStream(System.err);
		MInterpreter interp = new MInterpreter(proc);
		
		// evaluate the line
		
		System.out.println(interp.evalLine(m));
	}
}
