package gov.va.cpe.vpr.m4j.parser;

import static gov.va.cpe.vpr.m4j.parser.MParserUtils.evalNumericValue;
import static gov.va.cpe.vpr.m4j.parser.MParserUtils.infixToPostFix;
import static gov.va.cpe.vpr.m4j.parser.MParserUtils.parseRef;
import static gov.va.cpe.vpr.m4j.parser.MParserUtils.tokenize;
import static gov.va.cpe.vpr.m4j.parser.MParserUtils.tokenizeOps;
import static gov.va.cpe.vpr.m4j.parser.MParserUtils.strContains;
import static gov.va.cpe.vpr.m4j.parser.MParserUtils.getTokenType;
import static org.junit.Assert.*;
import gov.va.cpe.vpr.m4j.global.MVar;
import gov.va.cpe.vpr.m4j.lang.M4JRuntime;
import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExpr;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExprItem;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExprOper;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExprStrLiteral;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MFxnRef;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MLocalVarRef;
import gov.va.cpe.vpr.m4j.parser.MCmd.MCmdI;
import gov.va.cpe.vpr.m4j.parser.MCmd.MCmdN;
import gov.va.cpe.vpr.m4j.parser.MCmd.MCmdQ;
import gov.va.cpe.vpr.m4j.parser.MCmd.MCmdW;
import gov.va.cpe.vpr.m4j.parser.MCmd.MParseException;
import gov.va.cpe.vpr.m4j.parser.MLine.MComment;
import gov.va.cpe.vpr.m4j.parser.MLine.MEntryPoint;
import gov.va.cpe.vpr.m4j.parser.MParserUtils.TokenType;
import gov.va.cpe.vpr.m4j.parser.MToken.MLineItem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ParserTests {
	File test = null;
	String tokstr = "I $G(^VPRHTTP(0,\"listener\"))=\"stopped\" W !,\"Listener is already stopped.\",! Q";
	Set<String> delims1 = new HashSet<String>(Arrays.asList(" "));
	Set<String> delims2 = new HashSet<String>(Arrays.asList(" ", ","));
	MRoutine vprj;
	TestMContext ctx;
	
	public static class TestMContext extends M4JProcess {
		private ByteArrayOutputStream baos;

		public TestMContext() {
			this(null);
		}
		
		public TestMContext(M4JRuntime runtime) {
			super(runtime,0);
			// capture output in a string instead of to System.out
			baos = new ByteArrayOutputStream();
			setOutputStream(baos);
		}
		
		public String toString() {
			return baos.toString();
		}
		
	}
	
	@Before
	public void before() throws URISyntaxException, IOException {
		URL fileurl = ParserTests.class.getResource("testroutine.int");
		this.test = new File(fileurl.toURI());
		vprj = MRoutine.parseRoutineOutputFile(new FileInputStream(this.test)).get(0);
		ctx = new TestMContext();
	}
	
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
	public void testParseNumericValue() {
		// simple
		assertEquals(1, evalNumericValue("1"));
		assertEquals(0, evalNumericValue("0"));
		assertEquals(-1, evalNumericValue("-1"));
		assertEquals(1.1, evalNumericValue("1.1"));
		assertEquals(0.1, evalNumericValue("0.1"));
		assertEquals(1d, evalNumericValue("1.0"));
		assertEquals(1, evalNumericValue("+1"));
		
		// already numeric values
		assertEquals(10, evalNumericValue(new Integer(10)));
		assertEquals(10d, evalNumericValue(new Double(10)));
		
		// scientific notation
		assertEquals(1000, evalNumericValue("1E3"));
		assertEquals(1000, evalNumericValue("1e3"));
		assertEquals(-0.0011d, (Double) evalNumericValue("-1.1e-3"), .01d);
		
		// border cases
		assertEquals(0, evalNumericValue(""));
		assertEquals(0, evalNumericValue(null));
		
		// non-canatonical
		assertEquals(123.0, evalNumericValue("0000123.000"));
		assertEquals(123.12, evalNumericValue("+00123.12000"));
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
	public void testMRoutine() throws IOException {
		// basic metadata
		assertEquals(153, vprj.getLineCount());
		assertEquals("VPRJ", vprj.getName());
		
		// get a line (or the cached line)
		MLine line = vprj.getLine(10);
		assertNotNull(line);
		assertSame(line, vprj.getLine(10)); // shows that it does not reparse the line
		assertNull(vprj.getLine(-1));
		assertNull(vprj.getLine(153));
		assertNull(vprj.getLine(1000));
		assertNotNull(vprj.getLine(0));
		assertNotNull(vprj.getLine(152));
		
		// test offset vs index
		assertEquals(152, vprj.getLine(152).getOffset());
//		assertEquals(0, vprj.getLine(152).getOffset());
		
		// check entrypoint index
		Set<String> names = vprj.getEntryPointNames();
		assertEquals(26, names.size());
		assertTrue(vprj.hasEntryPoint("VPRJ"));
		assertTrue(vprj.hasEntryPoint("FULLRBLD"));
		assertFalse(vprj.hasEntryPoint("fullrbld")); // case sensitive
		assertFalse(vprj.hasEntryPoint("foo"));
		
		// check that the entrypoint lines are named, the rest of the lines are not
		for (String name : names) {
			MLine mline = vprj.getEntryPointLines(name).next();
			int idx = mline.getOffset(); 
			assertSame(mline, vprj.getLine(idx));
			
			// name should be the same, the next line should not be named
			assertEquals(name, mline.getLabel());
			assertNull(vprj.getLine(idx+1).getLabel());
		}
		
		// check entrypoint params
		
	}
		
		
	@Test
	public void testMRoutineStructure() {
		MCmd cmd;
		
		Iterator<MLine> itr = vprj.getEntryPointLines("ISYES");
		List<MLine> isyes = new ArrayList<>();
		while (itr.hasNext()) isyes.add(itr.next());
		assertEquals(8, isyes.size());
		
		// L0 is: ISYES(MSG) ; returns 1 if user answers yes to message, otherwise 0
		List<MLineItem<?>> l0 = isyes.get(0).getTokens();
		assertEquals(2, l0.size());
		assertEquals("ISYES(MSG)", l0.get(0).getValue());
		assertEquals(MEntryPoint.class, l0.get(0).getClass());
		
		// L1 is: N X
		List<MLineItem<?>> l1 = isyes.get(1).getTokens();
		assertEquals(1, l1.size());
		assertEquals(MCmdN.class, l1.get(0).getClass());
		cmd = (MCmd) l1.get(0);
		assertEquals("N X", cmd.getValue());
		
		// L2 is: W !,MSG
		List<MLineItem<?>> l2 = isyes.get(2).getTokens();
		assertEquals(1, l2.size());
		assertEquals(MCmdW.class, l2.get(0).getClass());
		cmd = (MCmd) l2.get(0);
		assertEquals("W !,MSG", cmd.getValue());
		
		// L3 is: R X:300 E  Q 0
		List<MLineItem<?>> l3 = isyes.get(3).getTokens();
		assertEquals(3, l3.size());
		assertEquals(MCmd.class, l3.get(0).getClass());
		cmd = (MCmd) l3.get(0);
		assertEquals("R X:300", cmd.getValue());
		cmd = (MCmd) l3.get(1);
		assertEquals("E  ", cmd.getValue());
		cmd = (MCmd) l3.get(2);
		assertEquals("Q 0", cmd.getValue());

		// L4 is: I $$UP^XLFSTR($E(X))="Y" Q 1
		List<MLineItem<?>> l4 = isyes.get(4).getTokens();
		assertEquals(2, l4.size());
		assertEquals(MCmdI.class, l4.get(0).getClass());
		cmd = (MCmd) l4.get(0);
		assertEquals("I $$UP^XLFSTR($E(X))=\"Y\"", cmd.getValue());
		cmd = (MCmd) l4.get(1);
		assertEquals("Q 1", cmd.getValue());
		
		// L5 is: Q 0
		List<MLineItem<?>> l5 = isyes.get(5).getTokens();
		assertEquals(1, l5.size());
		assertEquals(MCmdQ.class, l5.get(0).getClass());
		cmd = (MCmd) l5.get(0);
		assertEquals("Q 0", cmd.getValue());
	}
	
	@Test
	public void testMRoutineEntrypoint() {
		// first line of entrypoint=null should be line 1
		assertSame(vprj.getLine(0), vprj.getEntryPointLines(null).next());
		
		// therefore getEntryPointLines(null) should be identical to all lines
		Iterator<MLine> itr = vprj.getEntryPointLines(null);
		for (int i=0; i < vprj.children.size(); i++) {
			assertSame(vprj.getLine(i),itr.next());
		}
		
		// entry points dont necessarily quit/return and are from an entry point to the bottom of routine
		// check that each entry point starts contains all the line minus the start line index.
		int lineCount = vprj.getLineCount();
		for (String ep : vprj.getEntryPointNames()) {
			int startIdx = vprj.entryPointNames.get(ep);
			List<MLine> allLines = new ArrayList<>();
			for (itr = vprj.getEntryPointLines(ep); itr.hasNext();) allLines.add(itr.next());
			
			assertEquals((lineCount - startIdx), allLines.size());
		}
	}
	
	@Test
	public void testParseExpr() {
		String str = "$G(^VPRHTTP(0,\"listener\"))=\"stopped\"";
		MExpr expr = new MExpr(str, -1);
		
		List<MExprItem> items = expr.getExprItems();
		assertEquals(3, items.size());
		assertEquals(MFxnRef.class, items.get(0).getClass());
		assertEquals(MExprOper.class, items.get(1).getClass());
		assertEquals(MExprStrLiteral.class, items.get(2).getClass());
	}
	
	@Test
	public void testExecHelloWorld() throws MParseException {
		// the first command
		String m = " S FOO(\"bar\")=\"hello\",FOO(\"baz\")=\"world\" I FOO(\"bar\")=\"hello\" W !,FOO(\"bar\")_\" \"_FOO(\"baz\"),! ; should write hello world";
		MLine line = new MLine(m);
		line.eval(ctx, null);
		
//		System.out.println(MParserUtils.displayStructure(line, 100));
		
		// should result in writing hello world 
		assertEquals("\nhello world\n", ctx.toString());
		
		// check context has the local vars
		MVar foo = ctx.getLocal("FOO");
		assertEquals("hello", foo.val("bar"));
		assertEquals("world", foo.val("baz"));
	}
	
	@Test
	public void testExecForLoop() throws MParseException {
		// test the incremental form
		String m = "W !,\"Waiting 5 Secs\",! F I=1:1:5 H 1 W \".\"";
		MLine line = new MLine(m);
//		System.out.println(MParserUtils.displayStructure(line, 100));
		line.eval(ctx, null);
		assertEquals("\nWaiting 5 Secs\n.....", ctx.toString());
		
		// test the non-incremental form, write x 5 times, ending value of I should be 6
		ctx = new TestMContext();
		m = " S I=1 F  Q:I>5  W \"x\" S I=I+1";
		line = new MLine(m);
		line.eval(ctx, null);
		assertEquals("xxxxx", ctx.toString());
		assertEquals(6, ctx.getLocal("I").val());
	}
	
	@Test
	public void testConsoleLine() {
		// console lines cannot have a tag/entrypoint name, will throw syntax exception
		MLine m = new MLine("FOO(MSG) ; entrypoint-ish looking line");
		List<MLineItem<?>> toks = null;
		try {
			toks = m.getTokens();
			fail("Expected exception");
		} catch (MException.MSyntaxException ex) {
			// expected
		}
		
		// no longer need to be indented by one space
		toks = new MLine("W 1").getTokens();
		assertEquals(1, toks.size());
		assertTrue(toks.get(0) instanceof MCmd);

		// leading whitespace doesn't matter
		toks = new MLine("    W 1").getTokens();
		assertEquals(1, toks.size());
		assertTrue(toks.get(0) instanceof MCmd);
		
		// can still have a . for level indicator
		m = new MLine(" . . . W 1");
		toks = m.getTokens();
		assertEquals(1, toks.size());
		assertEquals(3, m.getLevel());
		assertTrue(toks.get(0) instanceof MCmd);
		assertEquals("W 1", toks.get(0).getValue());
		
		// can start with a ;
		m = new MLine("; comments");
		toks = m.getTokens();
		assertEquals(1, toks.size());
		assertTrue(toks.get(0) instanceof MComment);
		assertEquals("; comments", toks.get(0).getValue());
	}

	/** Testing the structure of an existing routine */
	@Test 
	public void testISYESStructure() throws IOException {
		// fetch lines of routine/entrypoint as list
		List<MLine> lines = new ArrayList<>();
		for(Iterator<MLine> itr = vprj.getEntryPointLines("ISYES"); itr.hasNext(); lines.add(itr.next()));

		assertEquals("ISYES(MSG) ; returns 1 if user answers yes to message, otherwise 0", lines.get(0).getValue());
		assertEquals(" N X", lines.get(1).getValue());
		assertEquals(" W !,MSG", lines.get(2).getValue());
		assertEquals(" R X:300 E  Q 0", lines.get(3).getValue());
		assertEquals(" I $$UP^XLFSTR($E(X))=\"Y\" Q 1", lines.get(4).getValue());
		assertEquals(" Q 0", lines.get(5).getValue());
		assertEquals(" ;", lines.get(6).getValue());
		
		// first line should be structured as labeled entrypoint line
		List<MLineItem<?>> toks = lines.get(0).getTokens();
		assertEquals(2, toks.size());
		assertTrue(toks.get(0) instanceof MEntryPoint);
		assertTrue(toks.get(1) instanceof MComment);
		MEntryPoint ep = (MEntryPoint) toks.get(0);
		assertEquals(1, ep.size());
		assertTrue(ep.children.get(0) instanceof MLocalVarRef);
		assertEquals("MSG", ep.children.get(0).getValue());
		
		// 5 command lines with 1,1,3,2,1 commands
		assertEquals(1, lines.get(1).getTokens().size());
		assertTrue(lines.get(1).getTokens().get(0) instanceof MCmd);
		assertEquals(1, lines.get(2).getTokens().size());
		assertTrue(lines.get(2).getTokens().get(0) instanceof MCmdW);
		assertEquals(3, lines.get(3).getTokens().size());
		assertTrue(lines.get(3).getTokens().get(0) instanceof MCmd);
		assertEquals(2, lines.get(4).getTokens().size());
		assertTrue(lines.get(4).getTokens().get(0) instanceof MCmdI);
		assertEquals(1, lines.get(5).getTokens().size());
		assertTrue(lines.get(5).getTokens().get(0) instanceof MCmdQ);

		// 1 comment line
		assertEquals(1, lines.get(6).getTokens().size());
		assertTrue(lines.get(6).getTokens().get(0) instanceof MComment);
	}

	@Test
	public void testParseWholeRO() throws IOException {
		// couple examples of header lines, make sure the regex gets them both
		assertTrue("VPRJ1^INT^1^63404;43618^0".matches(MRoutine.ROUTINE_HEADER_PATTERN));
		assertTrue("VPRJ^INT^1^62896,42379.33251^0".matches(MRoutine.ROUTINE_HEADER_PATTERN));
		
		// parse the included RO, should be 91 routines currently
		File jds = new File("lib/jds-0.7-S68-SNAPSHOT.ro");
		if (!jds.exists()) throw new FileNotFoundException();
		List<MRoutine> routines = MRoutine.parseRoutineOutputFile(new FileInputStream(jds));
		assertEquals(91, routines.size());
	}
	
	@Test
	public void testTokenTypeNumLiteral() {
		// matches simple numbers
		assertEquals(TokenType.NUM_LITERAL, getTokenType("+1", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("-1", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("0", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("0.0", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType(".1", null));
		
		// does not match
		assertEquals(TokenType.UNKNOWN, getTokenType("zero", null));
		assertEquals(TokenType.UNKNOWN, getTokenType(".", null));
		
		// with exponentials
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1e3", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1e+3", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1e-3", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1.1e3", null));
		
		assertEquals(TokenType.UNKNOWN, getTokenType("1.1e3.1", null));
	}
	
	@Test
	public void testTokenTypeStringLiteral() {
		// only true strings are quoted
		assertTrue(getTokenType("\"\"", null) == TokenType.STR_LITERAL);
		assertTrue(getTokenType("\"FOO\"", null) == TokenType.STR_LITERAL);
		
		// not strings
		assertFalse(getTokenType("'FOO'", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("FOO", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("\"FOO'", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("\"FOO", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("", null) == TokenType.STR_LITERAL);
	}
	
	@Test
	public void testTokenTypeCMD() {
		// various case-insensitive
		assertEquals(TokenType.COMMAND, getTokenType("W", null));
		assertEquals(TokenType.COMMAND, getTokenType("WRITE", null));
		assertEquals(TokenType.COMMAND, getTokenType("w", null));
		assertEquals(TokenType.COMMAND, getTokenType("wRiTe", null));
		
		// with post conditional
		assertEquals(TokenType.COMMAND, getTokenType("Q:FOO", null));
		
		// non-commands
		assertEquals(TokenType.UNKNOWN, getTokenType("FOO", null));
	}
	
	@Test
	public void test() {
		String line = " N % Q:'$D(X) \"\" I $L(X)*$G(Y)>245 Q \"\"";
		MParserUtils.dumpTokens(line);

		for (Iterator<MLine>itr=vprj.iterator(); itr.hasNext();) {
			MLine ml = itr.next();
			MParserUtils.dumpTokens(ml.getValue());
			
		}
	}
	
}
