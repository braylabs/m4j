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
		MUMPSParser ast = MInterpreter.parse("W \"FOO\"?1L");
		LinesContext lines = ast.lines();
		LineContext line = lines.line(0);
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

}
