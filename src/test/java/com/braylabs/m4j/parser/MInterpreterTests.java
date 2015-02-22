package com.braylabs.m4j.parser;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MVal.UnaryOp;
import com.braylabs.m4j.lang.RoutineProxy;

public class MInterpreterTests {
	
	private static M4JRuntime runtime = new M4JRuntime();
	private MInterpreter interp;
	private M4JProcess proc;
	
	public static class TestM4JProcess extends M4JProcess {
		private ByteArrayOutputStream baos;

		public TestM4JProcess() {
			this(runtime);
		}
		
		public TestM4JProcess(M4JRuntime runtime) {
			super(runtime,0);
			// capture output in a string instead of to System.out
			baos = new ByteArrayOutputStream();
			setOutputStream(baos);
		}
		
		/** Returns then clears the current output */
		public String toString() {
			String ret = baos.toString();
			baos.reset();
			return ret;
		}
	}


	@Before
	public void before() {
		proc = new TestM4JProcess();
		interp = new MInterpreter(proc);
		interp.setDebugMode(true);
	}
	
	@Test
	public void testNotWorkingYet() {
		// chained unary operators
		interp.evalLine("W --1");
		assertEquals("1", proc.toString());
	}
		

	@Test
	public void testOpPrecendence() {
		// strict left-to-right evaluation
		interp.evalLine("W 1+2*3");
		assertEquals("9", proc.toString());
		
		// use parens to force evaluation order
		interp.evalLine("W 1+(2*3)");
		assertEquals("7", proc.toString());
		
		// more parens
		interp.evalLine("W (1+(2*3))");
		assertEquals("7", proc.toString());
		
		// more parens
		interp.evalLine("W (1+2) * (3+4)");
		assertEquals("21", proc.toString());
		
		interp.evalLine("W -(123 - 3)"); // returns -120
		assertEquals("-120", proc.toString());
		
		interp.evalLine("W ((4 + 7) > (6 + 6))"); // false (0)
		assertEquals("0", proc.toString());
		
		interp.evalLine("W (4 + 7 > 6 + 6)"); // 7
		assertEquals("7", proc.toString());

		interp.evalLine("W 1+2*3-4*5"); // 25
		assertEquals("25", proc.toString());
		interp.evalLine("W 1+(2*3)-4*5"); // 15
		assertEquals("15", proc.toString());
		interp.evalLine("W 1+(2*(3-4))*5"); // -5
		assertEquals("-5", proc.toString());
		interp.evalLine("W 1+(((2*3)-4)*5)"); // 11
		assertEquals("11", proc.toString());
		
	}
	
	@Test
	public void testPatternMatch() {
		interp.evalLine("W \"FOO\"?1L");
		assertEquals("0", proc.toString());
		
		interp.evalLine("W \"123-12-1234\"?3N1\"-\"2N1\"-\"4N");
		assertEquals("1", proc.toString());

	}
	
	
	@Test
	public void testHelloWorld() {
		String m = " S FOO=\"Hello\",BAR=\"World\" W !,FOO_\" \"_BAR,1+1,!\n";
	
		// evaluate the line
		interp.evalLine(m);
		
		// validate check its output stream
		assertEquals("\nHello World2\n", proc.toString());
	}
	
	@Test
	public void testCMDIF() {
		String m = " S FOO(\"bar\")=\"hello\",FOO(\"baz\")=\"world!\" I FOO(\"bar\")=\"hello\" W !,FOO(\"bar\")_\" \"_FOO(\"baz\"),! ; should write hello world";

		// evaluate the line
		interp.evalLine(m);
		assertEquals("\nhello world!\n", proc.toString());
		
		// Evaluate line where IF is false, should abort whole line
		interp.evalLine("I 1>10 W \"truth\" W \"more truth\"");
		assertEquals("", proc.toString());
		
	}
	
	@Test
	public void testWeirdExpr() {
		// also trying to test some expressions that didn't initially work
		
		// test pre-conditional
		interp.evalLine("W:'1 1");
		assertEquals("", proc.toString());
		interp.evalLine("W:'0 1");
		assertEquals("1", proc.toString());
		interp.evalLine("W:1 1");
		assertEquals("1", proc.toString());
		interp.evalLine("W:0 1");
		assertEquals("", proc.toString());
		
		// test some goofy unary/binary ambiguity
		interp.evalLine("W -1");
		assertEquals("-1", proc.toString());
		
		interp.evalLine("W -1+1");
		assertEquals("0", proc.toString());
		
		interp.evalLine("W 1+-1");
		assertEquals("0", proc.toString());
		
		interp.evalLine("W -(1+1)");
		assertEquals("-2", proc.toString());

		// pos/neg string conversion
		interp.evalLine("W +\"FOO\"");
		assertEquals("0", proc.toString());

		interp.evalLine("W -\"FOO\"");
		assertEquals("0", proc.toString());

		interp.evalLine("W +\"12 monkeys\"");
		assertEquals("12", proc.toString());

		interp.evalLine("W -\"12 monkeys\"");
		assertEquals("-12", proc.toString());
	}
	
	@Test
	public void testIndentLine() {
		// indented lines should not execute on their own
		interp.evalLine(". . W 1");
		assertEquals("", proc.toString());
		
		// two un-indented lines should both run
		interp.evalLine("W 1\nW 2");
		assertEquals("12", proc.toString());
		
		// no indent should run of course
		interp.evalLine("W 1");
		assertEquals("1", proc.toString());
		
		// first line should run, not second line
		interp.evalLine("W 1\n. W 2");
		assertEquals("1", proc.toString());
	}
	
	@Test
	public void testInvokeSystemFunctions() {
		
		// multiple java methods mapped to $P, $PIECE, should pick one based on available params
		interp.evalLine("W $P(\"FE FI FO FUM\",\" \",2, 3)");
		assertEquals("FI FO", proc.toString());
		
		interp.evalLine("W $P(\"FE FI FO FUM\",\" \",2)");
		assertEquals("FI", proc.toString());

		interp.evalLine("W $P(\"FE FI FO FUM\",\" \")");
		assertEquals("FE", proc.toString());
	}
	
	@Test
	public void testInvokeSystemFunctions$O() {
		// some functions take variables and examine globals, etc
		MVar var = proc.getLocal("FOO");
		var.get("A").set(1);
		var.get("B").set(2);
		var.get("C").set(3);
		
		// subscripted
		interp.evalLine("W $O(FOO(\"A\"))");
		assertEquals("B", proc.toString());
		
		// unsubscripted
		interp.evalLine("W $O(FOO)");
		assertEquals("", proc.toString());
	}
	
	@Test
	public void testCMD_READ() {
		
	}
	
	@Test
	public void testLoadInvokeRoutine() throws IOException, URISyntaxException {
		File f = new File("src/main/mumps/XLFSTR.int");
		runtime.registerRoutine(new RoutineProxy.MInterpRoutineProxy(f));
		
		M4JProcess proc = new TestM4JProcess(runtime);
		proc.getInterpreter().evalLine("S SAY=\"hello \",NAME=\"world\" W $$UP^XLFSTR(SAY_NAME)");
		
		assertEquals("HELLO WORLD", proc.toString());
	}
}
