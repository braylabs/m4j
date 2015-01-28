package com.braylabs.m4j.parser.OLD;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.braylabs.m4j.parser.MParserUtilTests;
import com.braylabs.m4j.parser.MUMPSBaseVisitor;
import com.braylabs.m4j.parser.MUMPSLexer;
import com.braylabs.m4j.parser.MUMPSParser;
import com.braylabs.m4j.parser.MUMPSParser.CmdContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprContext;
import com.braylabs.m4j.parser.MUMPSParser.IndentedLineContext;
import com.braylabs.m4j.parser.MUMPSParser.LineContext;
import com.braylabs.m4j.parser.MUMPSParser.LineEPAndCommandsContext;
import com.braylabs.m4j.parser.MUMPSParser.LineEPOnlyContext;
import com.braylabs.m4j.parser.MUMPSParser.RefContext;
import com.braylabs.m4j.parser.MUMPSParser.RegularLineContext;

public class MParser3 extends MUMPSBaseVisitor<TOKEN> {
	
	@Override
	public TOKEN visit(ParseTree tree) {
		System.out.println("Visit");
		return super.visit(tree);
	}

	@Override
	public TOKEN visitRegularLine(RegularLineContext ctx) {
		System.out.println("visitRegularLine: " + ctx.getText());
		
		TOKEN line = new TOKEN(TokenType.LINE, ctx.getText());
		for (CmdContext cmd : ctx.cmdList().cmd()) {
			// construct new command token
			TOKEN tok = new TOKEN(TokenType.CMD, null);
			tok.name = cmd.ID().getText().toUpperCase();
			
			// add pc expression (if any)
			if (cmd.pce() != null) {
				tok.subtokens.add(new TOKEN(TokenType.PC, cmd.pce().getText()));
			}
			
			// then add each expression in the expr list
			if (cmd.exprList() == null) continue;
			
			tok.value = cmd.exprList().getText();
			for (ExprContext expr : cmd.exprList().expr()) {
				tok.subtokens.add(visit(expr));
			}
			
			line.subtokens.add(tok);
		}
		return line;
	}
	
	@Override
	public TOKEN visitExpr(ExprContext ctx) {
		System.out.println("visitExpr: " + ctx.getText());
		
		TOKEN expr = new TOKEN(TokenType.EXPR, ctx.getText());
		
		for (ParseTree item : ctx.children) {
			System.out.println(item);
		}
		
		return expr;
	}
	
	@Override
	public TOKEN visitRef(RefContext ctx) {
		TOKEN ref = new TOKEN(TokenType.REF, ctx.getText());

		
		return ref;
	}
	
	public static void parseLine(String line) {
		
		ANTLRInputStream input = new ANTLRInputStream(line);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		MUMPSParser parser = new MUMPSParser(tokens);
		LineContext tree = parser.line(); 
		System.out.println(tree.toStringTree(parser)); // print tree as text
		MParser3 visitor = new MParser3();
		System.out.println(visitor.visit(tree));
		
		for (int i=0; i < tree.getChildCount(); i++) {
			ParseTree node = tree.getChild(i);
			System.out.println("\n======\n");
			System.out.println(node.getClass());
			System.out.println(node.getPayload());
			System.out.println(node.getSourceInterval());
			System.out.println(node.getText());
		}
		
	}
	
	public static void parseFile(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		ANTLRInputStream input = new ANTLRInputStream(fis);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		MUMPSParser parser = new MUMPSParser(tokens);
		ParseTree tree = parser.file(); 
//		System.out.println(tree.toStringTree(parser)); // print tree as text
	}
	
	
	public static void main(String[] args) throws IOException, URISyntaxException {
		if (args.length > 0) {
			for (String arg : args) {
				System.out.println("Parsing: " + arg);
				File f = new File(arg);
				parseFile(f);
			}
			
			return;
		}
		String[] lines = new String[]{
			"XLFSTR ;ISC-SF/STAFF - String Functions ;12/19/06  09:45",
			" ;;8.0;KERNEL;**112,120,400,437**;Jul 10, 1995;Build 2",
			" ; ",
			"UP(X) Q $TR(X,\"abcdefghijklmnopqrstuvwxyz\",\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\")",
			" ;",
			"LOW(X) Q $TR(X,\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\",\"abcdefghijklmnopqrstuvwxyz\")",
			" ;",
			"STRIP(X,Y) Q $TR(X,$G(Y),\"\")",
			" ;",
			"REPEAT(X,Y) ;",
			" N N",
			" N % Q:'$D(X) \"\" I $L(X)*$G(Y)>245 Q \"\"",
			" S %=\"\",$P(%,X,$G(Y)+1)=\"\"",
			" Q %",
			" ;",
		};

		parseLine(" I ALPHA S LIST(NAME,PID)=SSN_\"^\"_ICN_\"^\"_DFN Q\n");
		
		for (String line : lines) {
//			System.out.println("PARSE: " + line);
//			parseLine(line + "\n");
		}
		
		URL fileurl = MParserUtilTests.class.getResource("testroutine.int");
//		parseFile(new File(fileurl.toURI()));


	}
	

}
