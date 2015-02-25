package com.braylabs.m4j.parser;

import static org.junit.Assert.*;

import org.junit.Test;

import com.braylabs.m4j.parser.MUMPSParser.CmdContext;
import com.braylabs.m4j.parser.MUMPSParser.CmdListContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprListContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprPatternContext;
import com.braylabs.m4j.parser.MUMPSParser.LineContext;
import com.braylabs.m4j.parser.MUMPSParser.LinesContext;
import com.braylabs.m4j.parser.MUMPSParser.LiteralContext;

/** This is a suite of tests to ensure the proper abstract syntax tree */
public class ASTParseTests {
	
	
	@Test
	public void testPatternExpr() {
		LineContext line = parseLine("W \"FOO\"?1L");
		CmdListContext cmdlist = line.cmdList();
		assertEquals(1, cmdlist.cmd().size());
		CmdContext cmd = cmdlist.cmd(0);
		assertEquals("W", cmd.ID().getText());
		ExprListContext exprList = cmd.exprList();
		assertEquals(1, exprList.expr().size());
		ExprContext expr = exprList.expr(0);
		
		assertEquals(3, expr.children.size());
		// TODO: Not optional that its an expression embedded in an expression
		assertEquals(LiteralContext.class, expr.getChild(0).getChild(0).getClass());
		assertEquals("?", expr.getChild(1).getText());
		assertEquals(ExprPatternContext.class, expr.getChild(2).getClass());
		
	}
	
	@Test
	public void testExpr() {
		
		// having problems with this expression: S %=%_$E(X,%1)
		// it was breaking it up into %=% then $E(...)
		LineContext line = parseLine("S %=%_$E(X,%1)");
		CmdListContext cmdList = line.cmdList();
		assertEquals(1, cmdList.cmd().size());
		CmdContext cmd = cmdList.cmd(0);
		assertEquals("S", cmd.ID().getText());
		
		
		// should only be one expression, not two
		assertEquals(1, cmd.exprList().expr().size());
	}
	
	public static LineContext parseLine(String line) {
		MUMPSParser ast = MInterpreter.parse(line);
		LinesContext lines = ast.lines();
		return lines.line(0);
	}

}
