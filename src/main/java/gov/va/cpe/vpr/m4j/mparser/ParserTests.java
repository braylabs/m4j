package gov.va.cpe.vpr.m4j.mparser;

import static gov.va.cpe.vpr.m4j.mparser.MParserUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import gov.va.cpe.vpr.m4j.MMap;
import gov.va.cpe.vpr.m4j.mparser.MCmd.MCmdI;
import gov.va.cpe.vpr.m4j.mparser.MCmd.MCmdQ;
import gov.va.cpe.vpr.m4j.mparser.MCmd.MCmdW;
import gov.va.cpe.vpr.m4j.mparser.MLine.MEntryPoint;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExpr;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExprItem;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExprOper;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExprStrLiteral;
import gov.va.cpe.vpr.m4j.mparser.MToken.MFxnRef;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
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
	
	@Before
	public void before() throws URISyntaxException, IOException {
		URL fileurl = ParserTests.class.getResource("testroutine.int");
		this.test = new File(fileurl.toURI());
		vprj = MRoutine.parseRoutineOutputFile(new FileInputStream(this.test)).get(0);
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
		assertEquals(152, vprj.getLine(152).getIndex());
		assertEquals(0, vprj.getLine(152).getOffset());
		
		// check entrypoint index
		Set<String> names = vprj.getEntryPointNames();
		assertEquals(26, names.size());
		assertTrue(vprj.hasEntryPoint("VPRJ"));
		assertTrue(vprj.hasEntryPoint("FULLRBLD"));
		assertFalse(vprj.hasEntryPoint("fullrbld")); // case sensitive
		assertFalse(vprj.hasEntryPoint("foo"));
		
		// check that the entrypoint lines are named, the rest of the lines are not
		for (String name : names) {
			MLine mline = vprj.getEntryPointLines(name).get(0);
			int idx = mline.getIndex(); 
			assertSame(mline, vprj.getLine(idx));
			
			// name should be the same, the next line should not be named
			assertEquals(name, mline.getLabel());
			assertNull(vprj.getLine(idx+1).getLabel());
		}
	}
		
		
	@Test
	public void testMRoutineStructure() {
		MCmd cmd;
		
		List<MLine> isyes = vprj.getEntryPointLines("ISYES");
		assertEquals(8, isyes.size());
		
		// L0 is: ISYES(MSG) ; returns 1 if user answers yes to message, otherwise 0
		List<MToken<?>> l0 = isyes.get(0).getTokens();
		assertEquals(2, l0.size());
		assertEquals("ISYES(MSG)", l0.get(0).getValue());
		assertEquals(MEntryPoint.class, l0.get(0).getClass());
		
		// L1 is: N X
		List<MToken<?>> l1 = isyes.get(1).getTokens();
		assertEquals(1, l1.size());
		assertEquals(MCmd.class, l1.get(0).getClass());
		cmd = (MCmd) l1.get(0);
		assertEquals("N X", cmd.getValue());
		
		// L2 is: W !,MSG
		List<MToken<?>> l2 = isyes.get(2).getTokens();
		assertEquals(1, l2.size());
		assertEquals(MCmdW.class, l2.get(0).getClass());
		cmd = (MCmd) l2.get(0);
		assertEquals("W !,MSG", cmd.getValue());
		
		// L3 is: R X:300 E  Q 0
		List<MToken<?>> l3 = isyes.get(3).getTokens();
		assertEquals(3, l3.size());
		assertEquals(MCmd.class, l3.get(0).getClass());
		cmd = (MCmd) l3.get(0);
		assertEquals("R X:300", cmd.getValue());
		cmd = (MCmd) l3.get(1);
		assertEquals("E  ", cmd.getValue());
		cmd = (MCmd) l3.get(2);
		assertEquals("Q 0", cmd.getValue());

		// L4 is: I $$UP^XLFSTR($E(X))="Y" Q 1
		List<MToken<?>> l4 = isyes.get(4).getTokens();
		assertEquals(2, l4.size());
		assertEquals(MCmdI.class, l4.get(0).getClass());
		cmd = (MCmd) l4.get(0);
		assertEquals("I $$UP^XLFSTR($E(X))=\"Y\"", cmd.getValue());
		cmd = (MCmd) l4.get(1);
		assertEquals("Q 1", cmd.getValue());
		
		// L5 is: Q 0
		List<MToken<?>> l5 = isyes.get(5).getTokens();
		assertEquals(1, l5.size());
		assertEquals(MCmdQ.class, l5.get(0).getClass());
		cmd = (MCmd) l5.get(0);
		assertEquals("Q 0", cmd.getValue());
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
	public void testExecHelloWorld() {
		// capture output in a string instead of to System.out
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		MContext ctx = new MContext();
		ctx.setOutputStream(baos);
		
		// the first command
		String m = " S FOO(\"bar\")=\"hello\",FOO(\"baz\")=\"world\" I FOO(\"bar\")=\"hello\" W !,FOO(\"bar\")_\" \"_FOO(\"baz\"),! ; should write hello world";
		MLine line = new MLine(m, 0);
		line.eval(ctx);
		
//		System.out.println(MParserUtils.displayStructure(line, 100));
		
		// should result in writing hello world 
		assertEquals("\nhello world\n", baos.toString());
		
		// check context has the local vars
		MMap foo = ctx.getLocal("FOO");
		assertEquals("hello", foo.getValue("bar"));
		assertEquals("world", foo.getValue("baz"));
	}
	
	@Test 
	@Ignore
	public void testISYESStructure() throws IOException {
		List<MLine> lines = vprj.getEntryPointLines("STOP");
		for (MLine line : lines) {
			System.out.println(MParserUtils.displayStructure(line, 10));
		}
	}

	@Test
	public void testParseWholeRO() throws IOException {
		File jds = new File("c:/data/hmp/hmp-main/src/main/mumps/jds.ro");
		List<MRoutine> routines = MRoutine.parseRoutineOutputFile(new FileInputStream(jds));
		assertEquals(69, routines.size());
	}
}
