package com.braylabs.m4j.parser;

import static com.braylabs.m4j.parser.MParserUtils.evalNumericValue;
import static com.braylabs.m4j.parser.MParserUtils.infixToPostFix;
import static com.braylabs.m4j.parser.MParserUtils.parseRef;
import static com.braylabs.m4j.parser.MParserUtils.strContains;
import static com.braylabs.m4j.parser.MParserUtils.tokenize;
import static com.braylabs.m4j.parser.MParserUtils.tokenizeOps;
import static com.braylabs.m4j.parser.MParserUtils.matches;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class MParserUtilTests {
	String tokstr = "I $G(^VPRHTTP(0,\"listener\"))=\"stopped\" W !,\"Listener is already stopped.\",! Q";
	Set<String> delims1 = new HashSet<String>(Arrays.asList(" "));
	Set<String> delims2 = new HashSet<String>(Arrays.asList(" ", ","));
	
	@Test
	public void testDetermineLineLevel() {
		// default level 0
		assertEquals(0, MParserUtils.determineLineLevel("W 'foo'"));
		assertEquals(0, MParserUtils.determineLineLevel(" W 'foo'"));
		assertEquals(0, MParserUtils.determineLineLevel(" ; comment xyz "));
		
		// indented
		assertEquals(1, MParserUtils.determineLineLevel(" . I xyz ; this is inside a loop."));
		assertEquals(3, MParserUtils.determineLineLevel(" . . . I xyz ; a space between .'s is how the VA does it"));
		assertEquals(3, MParserUtils.determineLineLevel(" ... I xyz ; I think this is legal too."));
		
		// border cases
		assertEquals(0, MParserUtils.determineLineLevel(null));
		assertEquals(0, MParserUtils.determineLineLevel(""));
	}
	
	@Test
	public void testParseRoutineName() {
		// regular names
		assertEquals("foo", MParserUtils.parseRoutineName("foo"));
		assertEquals("%foo", MParserUtils.parseRoutineName("%foo"));
		assertEquals("foo123", MParserUtils.parseRoutineName("foo123"));
		
		// ignore the rest of the line
		assertEquals("foo", MParserUtils.parseRoutineName("foo; this is the foo entry point"));
		assertEquals("foo", MParserUtils.parseRoutineName("foo ; this is the foo entry point"));
		assertEquals("foo", MParserUtils.parseRoutineName("foo(x,y,z)"));
		
		// border cases
		assertEquals(null, MParserUtils.parseRoutineName("123foo"));
		assertEquals(null, MParserUtils.parseRoutineName(null));
		assertEquals(null, MParserUtils.parseRoutineName(""));
		assertEquals(null, MParserUtils.parseRoutineName(" foo"));
	}
	
	@Test
	public void testDefaultTokenizer() {
		List<String> toks = tokenize("W x,y,z");
		assertEquals(2, toks.size());
		assertEquals("W", toks.get(0));
		assertEquals("x,y,z", toks.get(1));
		
		// adjacent delimeters return null token value
		/*
		assertEquals(6, tokenize("W x, y, z").size());
		assertEquals("W", tokenize("W x, y, z").get(0));
		assertEquals("x", tokenize("W x, y, z").get(1));
		assertEquals(null, tokenize("W x, y, z").get(2));
		assertEquals("y", tokenize("W x, y, z").get(3));
		assertEquals(null, tokenize("W x, y, z").get(4));
		assertEquals("z", tokenize("W x, y, z").get(5));
		*/
		
		// basic quote handling
		assertEquals(2, tokenize("hello world").size());
		assertEquals(1, tokenize("\"hello world\"").size());
		
		// basic paren matching to ignore delimiters
		assertEquals(1, tokenize("(1,2,3)", ',').size());
		assertEquals(3, tokenize("(1,2,3),(4,5,6),(7,8,9)",',').size());
		
		// handles comments as well
		toks = tokenize("Q ; quit the program "); 
		assertEquals(2, toks.size());
		assertEquals("Q", toks.get(0));
		assertEquals("; quit the program ", toks.get(1));
		
		// boundary cases
		assertEquals(1, tokenize("", ' ').size());
		assertEquals(0, tokenize(null).size());
	}
	
	@Test
	public void testTokenizerOps() {
		List<String> toks;
		
		// simple test
		toks = tokenizeOps("a=1");
		assertEquals(3, toks.size());
		assertEquals("a", toks.get(0));
		assertEquals("=", toks.get(1));
		assertEquals("1", toks.get(2));
		
		// test when delims(s) are at the end of the string
		toks = tokenizeOps("a=1=");
		assertEquals(4, toks.size());
		assertEquals("a", toks.get(0));
		assertEquals("=", toks.get(1));
		assertEquals("1", toks.get(2));
		assertEquals("=", toks.get(3));
		
		// multiple charater operators should be one token
		toks = tokenizeOps("5<=9>=5");
		assertEquals(5, toks.size());
		assertEquals("5", toks.get(0));
		assertEquals("<=", toks.get(1));
		assertEquals("9", toks.get(2));
		assertEquals(">=", toks.get(3));
		assertEquals("5", toks.get(4));
		
		// if a multiple character operator is not an operator, its multiple operators
		toks = tokenizeOps("5==6");
		assertEquals(4, toks.size());
		assertEquals("5", toks.get(0));
		assertEquals("=", toks.get(1));
		assertEquals("=", toks.get(2));
		assertEquals("6", toks.get(3));
		
		// test ignore delims inside quotes
		toks = tokenizeOps("\"a==b\"'=\"b==c\"");
		assertEquals(3, toks.size());
		assertEquals("\"a==b\"", toks.get(0));
		assertEquals("'=", toks.get(1));
		assertEquals("\"b==c\"", toks.get(2));
		
		// test ignore delims inside parens
		toks = tokenizeOps("truthy(3>4)=truthy(4<3)");
		assertEquals(3, toks.size());
		assertEquals("truthy(3>4)", toks.get(0));
		assertEquals("=", toks.get(1));
		assertEquals("truthy(4<3)", toks.get(2));
		
		// W 1+2*3 => 9
		toks = tokenizeOps("1+2*3");
		assertEquals(5, toks.size());
		assertEquals("1", toks.get(0));
		assertEquals("+", toks.get(1));
		assertEquals("2", toks.get(2));
		assertEquals("*", toks.get(3));
		assertEquals("3", toks.get(4));

		// W 1+(2*3) => 7
		toks = tokenizeOps("1+(2*3)");
		assertEquals(3, toks.size());
		assertEquals("1", toks.get(0));
		assertEquals("+", toks.get(1));
		assertEquals("(2*3)", toks.get(2));
	}
	
	@Test
	public void testTokenizerInfixToPostfix() {
		List<String> toks;
		toks = infixToPostFix("1+2*3");
		
		// W 1+2*3 => 9
		assertEquals(5, toks.size());
		assertEquals("1", toks.get(0));
		assertEquals("2", toks.get(1));
		assertEquals("+", toks.get(2));
		assertEquals("3", toks.get(3));
		assertEquals("*", toks.get(4));
		
		// W 1+(2*3) => 7
		toks = infixToPostFix("1+(2*3)");
		assertEquals(5, toks.size());
		assertEquals("1", toks.get(0));
		assertEquals("2", toks.get(1));
		assertEquals("3", toks.get(2));
		assertEquals("*", toks.get(3));
		assertEquals("+", toks.get(4));
		
		// test negation operator
		toks = infixToPostFix("'1");
		assertEquals(2, toks.size());
		assertEquals("1", toks.get(0));
		assertEquals("'", toks.get(1));
		
		// test ignore delims inside parens
		toks = infixToPostFix("foo=get(a,b,c)");
		assertEquals(3, toks.size());
		assertEquals("foo", toks.get(0));
		assertEquals("get(a,b,c)", toks.get(1));
		assertEquals("=", toks.get(2));

		
		// test ignore delims inside parens
		toks = infixToPostFix("foo=truthyfxn(4<3)");
		assertEquals(3, toks.size());
		assertEquals("foo", toks.get(0));
		assertEquals("truthyfxn(4<3)", toks.get(1));
		assertEquals("=", toks.get(2));
		
		// I=I+1 needs operator precedence (only for the ='s)
		toks = infixToPostFix("I=I+1");
		assertEquals(5, toks.size());
		assertEquals("I", toks.get(0));
		assertEquals("I", toks.get(1));
		assertEquals("1", toks.get(2));
		assertEquals("+", toks.get(3));
		assertEquals("=", toks.get(4));
	}
	
	@Test
	public void testTokenizerQuotes() {
		List<String> toks = tokenize(tokstr, delims1, true, false, false, true, false);
		assertEquals(5, toks.size());
		assertEquals("I", toks.get(0));
		assertEquals("$G(^VPRHTTP(0,\"listener\"))=\"stopped\"", toks.get(1));
		assertEquals("W", toks.get(2));
		assertEquals("!,\"Listener is already stopped.\",!", toks.get(3));
		assertEquals("Q", toks.get(4));

		// this is a empty string 
		toks = tokenize("a,\"\",c", ',');
		assertEquals(3, toks.size());
		assertEquals("a", toks.get(0));
		assertEquals("\"\"", toks.get(1));
		assertEquals("c", toks.get(2));
		
		// should be able to recognize escaped quotes
		toks = tokenize("\"\"\"\"",',');
		assertEquals(1, toks.size());
		assertEquals("\"\"\"", toks.get(0));
	}
	
	@Test
	public void testTokenizerParensAndQuotes() {
		List<String> toks = tokenize(tokstr, delims2, true, true, false, true, false);
		assertEquals(7, toks.size());
		assertEquals("I", toks.get(0));
		assertEquals("$G(^VPRHTTP(0,\"listener\"))=\"stopped\"", toks.get(1));
		assertEquals("W", toks.get(2));
		assertEquals("!", toks.get(3));
		assertEquals("\"Listener is already stopped.\"", toks.get(4));
		assertEquals("!", toks.get(5));
		assertEquals("Q", toks.get(6));
	}

	@Test
	public void testFixedWidthStr() {
		assertEquals("     ", MParserUtils.fixedWidthStr(null, 5));
		assertEquals("     ", MParserUtils.fixedWidthStr("", 5));
		assertEquals("ABC  ", MParserUtils.fixedWidthStr("ABC", 5));
		assertEquals("ABCDE", MParserUtils.fixedWidthStr("ABCDEFGHIJKLMNOPQRSTUV", 5));
	}
	
	@Test
	public void testParseStringLiteral() {
		String test = "   \"foo\"    \"bar\"   ";
		
		assertEquals("foo", MParserUtils.parseStringLiteral(test, 0));
		assertEquals("foo", MParserUtils.parseStringLiteral(test, 2));
		assertEquals("bar", MParserUtils.parseStringLiteral(test, 9));
		assertEquals("bar", MParserUtils.parseStringLiteral(test, 11));
		
		// test escaping
		assertEquals("\"", MParserUtils.parseStringLiteral("\"\"\"\"", 0)); // 4 quotes returns 1 quote
		
		// should not find anything
		assertEquals(null, MParserUtils.parseStringLiteral(null, 0));
		assertEquals(null, MParserUtils.parseStringLiteral("", 0));
		assertEquals(null, MParserUtils.parseStringLiteral("abc", 0));
		assertEquals(null, MParserUtils.parseStringLiteral("abc", -1));
		assertEquals(null, MParserUtils.parseStringLiteral("abc", 10));
	}
	@Test
	public void testEvalNumericValue() {
		// simple
		assertEquals(1, evalNumericValue("1"));
		assertEquals(0, evalNumericValue("0"));
		assertEquals(-1, evalNumericValue("-1"));
		assertEquals(1.1, evalNumericValue("1.1"));
		assertEquals(0.1, evalNumericValue("0.1"));
		assertEquals(1d, evalNumericValue("1.0"));
		assertEquals(1, evalNumericValue("+1"));
		
		// leading digits will be the numeric value
		assertEquals(12, evalNumericValue("12 monkeys"));
		assertEquals(0, evalNumericValue("asdf123fdsa"));
		
		// already numeric values
		assertEquals(10, evalNumericValue(new Integer(10)));
		assertEquals(10d, evalNumericValue(new Double(10)));
		
		// scientific notation
		assertEquals(1000, evalNumericValue("1E3"));
		assertEquals(1000, evalNumericValue("1e3"));
		assertEquals(-0.0011d, (Double) evalNumericValue("-1.1e-3"), .01d);
		assertEquals(21, evalNumericValue("21eee13"));
		
		// border cases
		assertEquals(0, evalNumericValue(""));
		assertEquals(0, evalNumericValue(null));
		
		// booleans
		assertEquals(0, evalNumericValue(Boolean.FALSE));
		assertEquals(1, evalNumericValue(Boolean.TRUE));
		
		// non-canatonical
		assertEquals(123.0, evalNumericValue("0000123.000"));
		assertEquals(123.12, evalNumericValue("+00123.12000"));

		// strange case of multiple prefix operators: "-+-++-7" does nothing here (only works on unary operator)
		assertEquals(0, evalNumericValue("-+-++-7"));
	}
	
	@Test
	public void testParseNumericLiteral() {
		
		assertEquals("1", MParserUtils.parseNumericLiteral("1", 0));
		assertEquals("12", MParserUtils.parseNumericLiteral("12", 0));
		assertEquals("123", MParserUtils.parseNumericLiteral("123", 0));
		
		// test fractional values
		assertEquals("1.5", MParserUtils.parseNumericLiteral("1.5", 0));
		assertEquals(".5", MParserUtils.parseNumericLiteral(".5", 0));
		assertEquals("0.5", MParserUtils.parseNumericLiteral("0.5", 0));
		
		// check that it recognizes exponential values
		assertEquals("8e7", MParserUtils.parseNumericLiteral("8e7", 0));
		assertEquals("8E7", MParserUtils.parseNumericLiteral("8E7", 0));
		assertEquals("-8E7", MParserUtils.parseNumericLiteral("-8E7", 0));
		assertEquals("8E-7", MParserUtils.parseNumericLiteral("8E-7", 0));
		assertEquals("8E7.5", MParserUtils.parseNumericLiteral("8E7.5", 0));
		
		// test positions other than start
		assertEquals("2", MParserUtils.parseNumericLiteral("1 2 3", 1));
		assertEquals("3", MParserUtils.parseNumericLiteral("1 2 3", 3));
		
		// cases it should not find anything
		assertEquals(null, MParserUtils.parseNumericLiteral("abc", 0));
		assertEquals(null, MParserUtils.parseNumericLiteral("X-Y", 0));
		assertEquals(null, MParserUtils.parseNumericLiteral(null, 0));
		assertEquals(null, MParserUtils.parseNumericLiteral("abc", -1));
		assertEquals(null, MParserUtils.parseNumericLiteral("abc", 10));
	}
	
	@Test
	public void testStrContains() {
		// matches
		assertTrue(strContains("abc", 'c'));
		assertTrue(strContains("1,2,3", '2'));
		assertTrue(strContains("1,2,3", ','));
		assertTrue(strContains("abc", 'a','b','c'));
		
		// no match due to being inside quotes or parens
		assertFalse(strContains("FOO(1,2,3)", ','));
		assertFalse(strContains("FOO(1,2,3)", '1'));
		assertFalse(strContains("\"ABC\"", 'A'));

		// border cases
		assertFalse(strContains(null, '\0'));
		assertFalse(strContains("", '\0'));
		assertFalse(strContains(""));
		
		// real world: check for operators in the expression (outside parens)
		assertTrue(strContains("1+2", MCmd.ALL_OPERATOR_CHARS));
		assertFalse(strContains("FOO(1+2)", MCmd.ALL_OPERATOR_CHARS));
	}
	
	@Test
	public void testParseRef() {
		String[] strs;
		
		// routine call w/ entry point
		strs = parseRef("@$$FOO^BAR(baz)");
		assertEquals("@$$", strs[0]);
		assertEquals("FOO", strs[1]);
		assertEquals("BAR", strs[2]);
		assertEquals("baz", strs[3]);
		
		// routine ref w/o any args
		strs = parseRef("FOO^BAR");
		assertEquals(null, strs[0]);
		assertEquals("FOO", strs[1]);
		assertEquals("BAR", strs[2]);
		assertEquals(null, strs[3]);

		// $$FOO^BAR
		strs = parseRef("$$FOO^BAR");
		assertEquals("$$", strs[0]);
		assertEquals("FOO", strs[1]);
		assertEquals("BAR", strs[2]);
		assertEquals(null, strs[3]);

		// $$FOO
		strs = parseRef("$$FOO");
		assertEquals("$$", strs[0]);
		assertEquals("FOO", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals(null, strs[3]);

		// $$FOO^BAR()
		strs = parseRef("$$FOO^BAR()");
		assertEquals("$$", strs[0]);
		assertEquals("FOO", strs[1]);
		assertEquals("BAR", strs[2]);
		assertEquals(null, strs[3]);
		
		// ^BAR
		strs = parseRef("^BAR");
		assertEquals("^", strs[0]);
		assertEquals("BAR", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals(null, strs[3]);
		
		// ^BAR()
		strs = parseRef("^BAR()");
		assertEquals("^", strs[0]);
		assertEquals("BAR", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals(null, strs[3]);
		
		// % is valid local variable name
		strs = parseRef("%");
		assertEquals(null, strs[0]);
		assertEquals("%", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals(null, strs[3]);
		
		strs = parseRef("%(A)");
		assertEquals(null, strs[0]);
		assertEquals("%", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("A", strs[3]);

		// entry point call 
		strs = parseRef("$$FOO(baz)");
		assertEquals("$$", strs[0]);
		assertEquals("FOO", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("baz", strs[3]);
		
		// function call
		strs = parseRef("$P(baz)");
		assertEquals("$", strs[0]);
		assertEquals("P", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("baz", strs[3]);
		
		// utility routine call
		strs = parseRef("^%CD");
		assertEquals("^", strs[0]);
		assertEquals("%CD", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals(null, strs[3]);

		// global ref
		strs = parseRef("^BAR(1,2,3)");
		assertEquals("^", strs[0]);
		assertEquals("BAR", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("1,2,3", strs[3]);
		
		// local variable ref
		strs = parseRef("X");
		assertEquals(null, strs[0]);
		assertEquals("X", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals(null, strs[3]);
		
		// local variable ref
		strs = parseRef("X(1,2,3)");
		assertEquals(null, strs[0]);
		assertEquals("X", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("1,2,3", strs[3]);
		
		// nested parser case
		strs = parseRef("$G(^VPRHTTP($G(foo),\"listener\"))");
		assertEquals("$", strs[0]);
		assertEquals("G", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("^VPRHTTP($G(foo),\"listener\")", strs[3]);
		
		// expressions as args are ok
		strs = parseRef("$G(A+B,1+2)");
		assertEquals("$", strs[0]);
		assertEquals("G", strs[1]);
		assertEquals(null, strs[2]);
		assertEquals("A+B,1+2", strs[3]);

		// expression should not work
		assertNull(parseRef("A+B"));
		assertNull(parseRef("A,B,C"));
		assertNull(parseRef("'A"));
		assertNull(parseRef("$$FOO^BAR(X)=1"));
		
		// should not match literals
		assertNull(parseRef("\"FOO\""));
		assertNull(parseRef("+1"));
		assertNull(parseRef("-1"));
		assertNull(parseRef("1E4"));

	}
	
	@Test
	public void testMatch() {
		assertFalse(matches("FOO", "1L"));
		assertTrue(matches("4", "1L"));
	}
	

	
}
