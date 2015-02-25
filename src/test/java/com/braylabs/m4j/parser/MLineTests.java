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
	

	@Before
	public void before() throws URISyntaxException, IOException {
		URL fileurl = MParserUtilTests.class.getResource("testroutine.int");
		this.test = new File(fileurl.toURI());
		vprj = MRoutine.parseRoutineOutputFile(new FileInputStream(this.test)).get(0);
		ctx = new TestMContext();
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

	
	@Test
	public void testExtraWhitespace() {
		List<MLineItem<?>> toks = new MLine("N %,%1 S %=\"\" F %1=$L(X):-1:1 S %=%_$E(X,%1)").getTokens();
		assertEquals(4, toks.size());

		// same thing with extra spaces should produce the same result
		toks = new MLine("N % , %1 S % = \"\" F %1 = $L(X) : -1 : 1 S % = % _ $E(X, %1)").getTokens();
		assertEquals(4, toks.size());
	}
	

}
