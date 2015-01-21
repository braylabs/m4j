package com.braylabs.m4j.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.junit.Test;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.parser.MLine;
import com.braylabs.m4j.parser.AbstractMToken.MExpr;
import com.braylabs.m4j.parser.AbstractMToken.MExprItem;
import com.braylabs.m4j.parser.AbstractMToken.MExprOper;
import com.braylabs.m4j.parser.AbstractMToken.MExprStrLiteral;
import com.braylabs.m4j.parser.AbstractMToken.MFxnRef;
import com.braylabs.m4j.parser.AbstractMToken.MLocalVarRef;
import com.braylabs.m4j.parser.MCmd.MCmdI;
import com.braylabs.m4j.parser.MCmd.MCmdN;
import com.braylabs.m4j.parser.MCmd.MCmdQ;
import com.braylabs.m4j.parser.MCmd.MCmdW;
import com.braylabs.m4j.parser.MCmd.MParseException;
import com.braylabs.m4j.parser.MLine.MComment;
import com.braylabs.m4j.parser.MLine.MEntryPoint;
import com.braylabs.m4j.parser.MToken.MLineItem;

public class MLineTests {
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
		URL fileurl = MParserUtilTests.class.getResource("testroutine.int");
		this.test = new File(fileurl.toURI());
		vprj = MRoutine.parseRoutineOutputFile(new FileInputStream(this.test)).get(0);
		ctx = new TestMContext();
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
	public void testExtraWhitespace() {
		List<MLineItem<?>> toks = new MLine("N %,%1 S %=\"\" F %1=$L(X):-1:1 S %=%_$E(X,%1)").getTokens();
		assertEquals(4, toks.size());

		// same thing with extra spaces should produce the same result
		toks = new MLine("N % , %1 S % = \"\" F %1 = $L(X) : -1 : 1 S % = % _ $E(X, %1)").getTokens();
		assertEquals(4, toks.size());
	}
	
	@Test
	public void testPostFixNotation() {
		List<MLineItem<?>> toks = new MLine("Q:'$D(IN) \"\" Q:$D(SPEC)'>9 IN N %1,%2,%3,%4,%5,%6,%7,%8").getTokens();
		assertEquals(2, toks.size());
		
	}
}
