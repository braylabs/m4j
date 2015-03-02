package com.braylabs.m4j.lang;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JInterpreter2;
import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MUMPS2Lexer;
import com.braylabs.m4j.lang.MUMPS2Parser;
import com.braylabs.m4j.lang.MUMPS2Parser.ArgsContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdArgContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdPostCondContext;
import com.braylabs.m4j.lang.MUMPS2Parser.EntryPointContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprBinaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprFuncContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprGroupContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprIndirContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLiteralContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprMatchContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprRefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprUnaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LineContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LinesContext;
import com.braylabs.m4j.lang.MUMPS2Parser.RefContext;
import com.braylabs.m4j.lang.RoutineProxy.MInterpRoutineProxy;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JEntryPoint;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JRoutine;

public class M4JInterpreter2Tests {
	
	private static MInterpRoutineProxy XLFSTR;
	private static M4JRuntime runtime = new M4JRuntime();
	private M4JInterpreter2 interp;
	private M4JProcess proc;
	MUMPS2Parser parser;
	
	static {
		try {
			XLFSTR = new MInterpRoutineProxy(new File("src/main/mumps/XLFSTR.int"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
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
	public void setup() throws IOException {
		// load sample routines
		runtime.registerRoutine(XLFSTR);
		
		// setup the parser with empty string for now
		ANTLRInputStream input = new ANTLRInputStream();
		MUMPS2Lexer lexer = new MUMPS2Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		parser = new MUMPS2Parser(tokens);
		proc = new TestM4JProcess();
		interp = spy(new M4JInterpreter2(proc));
//		Mockito.doCallRealMethod().when(interp).visitExprBinary(any(ExprBinaryContext.class));
	}
	
	@Test
	public void testLiteralExpr() {
		eval("W 1");
		assertEquals("1", proc.toString());
		
		eval("W \"FOO\"");
		assertEquals("FOO", proc.toString());
		
		eval("W !,1,2,3,!");
		assertEquals("\n123\n", proc.toString());
		
//		eval("W ?10,10,!");
//		assertEquals("\n          10\n", proc.toString());
		
		eval("W \"12 monkeys\"");
		assertEquals("12 monkeys", proc.toString());

		eval("W +\"12 monkeys\"");
		assertEquals("12", proc.toString());

		// TOOD: fill in evaluation stack
		// TODO: fancy string/number conversion here? "12 monkeys"
	}

	@Test
	public void testBinaryExpr() {
		eval("W 1+2");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprBinary(any(ExprBinaryContext.class));
		verify(interp, times(2)).visitExprLiteral(any(ExprLiteralContext.class));
		assertEquals("3", proc.toString());
		
		reset(interp);
		eval("W 1+2*3");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp, times(2)).visitExprBinary(any(ExprBinaryContext.class));
		assertEquals("9", proc.toString());
	}
	
	@Test
	public void testGroupedExpr() {
		eval("W (1+2)");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprGroup(any(ExprGroupContext.class));
		verify(interp).visitExprBinary(any(ExprBinaryContext.class));
		assertEquals("3", proc.toString());
		
		reset(interp);
		eval("W (1+(2*3))");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp, times(2)).visitExprGroup(any(ExprGroupContext.class));
		verify(interp, times(2)).visitExprBinary(any(ExprBinaryContext.class));
		assertEquals("7", proc.toString());
	}
	
	@Test
	public void testUnaryExpr() {
		eval("W -(1+2)");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprGroup(any(ExprGroupContext.class));
		verify(interp).visitExprBinary(any(ExprBinaryContext.class));
		verify(interp).visitExprUnary(any(ExprUnaryContext.class));

		assertEquals("-3", proc.toString());
		
		// chained unary operators
		reset(interp);
		eval("W --1");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp, times(2)).visitExprUnary(any(ExprUnaryContext.class));
		assertEquals("1", proc.toString());
		
		// chained unary operators
		reset(interp);
		eval("W -(+(--1))");
		assertEquals("-1", proc.toString());
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
	public void testEntryPointLine() {
		// entry point with arguments and commands on a single line
		eval("\nFOO(A,B,C) W A,B,C");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitEntryPoint(any(EntryPointContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		
		// entry point with empty arguments, but with commands on a single line
		reset(interp);
		eval("\nFOO() W A,B,C");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitEntryPoint(any(EntryPointContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		
		// entry point with no arguments, but with commands on a single line
		reset(interp);
		eval("\nFOO W A,B,C");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitEntryPoint(any(EntryPointContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
	}
	
	@Test
	public void testIndirection() {
		eval("S X=\"FOO\",FOO=10 W @X");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp, times(2)).visitCmd(any(CmdContext.class));
		verify(interp).visitExprIndir(any(ExprIndirContext.class));
		assertEquals("10", proc.toString());
		
		// TODO: test other forms of indirection
	}
	
	@Test
	public void testPostConditional() {
		// expression evaluates
		eval("W:1 1+2");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmdPostCond(any(CmdPostCondContext.class));
		verify(interp, times(3)).visitExprLiteral(any(ExprLiteralContext.class));

		// expression never evaluates
		reset(interp);
		eval("W:0 1+2");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmdPostCond(any(CmdPostCondContext.class));
		verify(interp).visitExprLiteral(any(ExprLiteralContext.class));

	}
	
	@Test
	public void test$Select() {
		
		eval("W $S(1=1:\"ONE\",1=2:2)");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprFunc(any(ExprFuncContext.class));
		verify(interp, times(2)).visitExprBinary(any(ExprBinaryContext.class));
		verify(interp, times(6)).visitExprLiteral(any(ExprLiteralContext.class));
		verify(interp).visitArgs(any(ArgsContext.class));
		
		assertEquals("ONE", proc.toString());

	}
	
	@Test
	public void testHelloWorld() {
		eval("S FOO=\"Hello\",BAR=\"World\" W !,FOO_\" \"_BAR,1+1,!");
		
		// validate check its output stream
		assertEquals("\nHello World2\n", proc.toString());
	}
	
	@Test
	public void testCMDW() {
		// write a variable should write the val()
		eval("S FOO=10 W FOO");
		assertEquals("10", proc.toString());

		// ! is a literal meaning new line
		eval("W !,10,!");
		assertEquals("\n10\n", proc.toString());

		// ?10 indicates indent to the 10th character is a literal meaning new line
		eval("W ?10,10");
		assertEquals("          10", proc.toString());

		// # indicates clear screen
		eval("W #,10");
		assertEquals("\r\f10", proc.toString());

		// compound use is ok too
//		eval("W !?10,10,!");
//		assertEquals("\n          10\n", proc.toString());
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
	public void testCallRoutineNoArgs() {
		// GO/DO style routine call
		eval("D UP^XLFSTR");
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
//		verify(interp).visitExprRef(any(ExprRefContext.class));
//		verify(interp).visitRefRoutine(any(RefRoutineContext.class));

		// immediate execution style routine call
		reset(interp);
		eval("W $$UP^XLFSTR(\"foo\")");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprRef(any(ExprRefContext.class));
		verify(interp).visitRef(any(RefContext.class));
		
		// invoke entry point within current routine 
		reset(interp);
		eval("W $$UP");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprRef(any(ExprRefContext.class));
		verify(interp).visitRef(any(RefContext.class));
	}
	
	@Test
	public void testCallRoutineWithArgs() {

		// immediate execution style routine call
		reset(interp);
		eval("W $$UP^XLFSTR(1,2)");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprRef(any(ExprRefContext.class));
		verify(interp).visitRef(any(RefContext.class));
		verify(interp, times(2)).visitExprLiteral(any(ExprLiteralContext.class));
		
		// invoke entry point within current routine 
		reset(interp);
		eval("W $$UP(1)");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprRef(any(ExprRefContext.class));
		verify(interp).visitRef(any(RefContext.class));
		verify(interp).visitExprLiteral(any(ExprLiteralContext.class));

	}
	
	// TODO: Test errors (<UNDEFINED>, etc.)
	
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
	public void testPatternMatch() {
		interp.evalLine("W \"FOO\"?1L");
		verify(interp).visitExprMatch(any(ExprMatchContext.class));
		assertEquals("0", proc.toString());
		
		reset(interp);
		interp.evalLine("W \"123-12-1234\"?3N1\"-\"2N1\"-\"4N");
		verify(interp).visitExprMatch(any(ExprMatchContext.class));
		assertEquals("1", proc.toString());
	}
	
	@Test
	public void testIndentLine() {
		// indented lines should not execute on their own
		interp.evalLine(". . W 1");
		assertEquals("", proc.toString());
		
		// two un-indented lines should both run
		reset(interp);
		interp.evalLine("W 1\nW 2");
		verify(interp).visitLines(any(LinesContext.class));
		verify(interp, times(2)).visitLine(any(LineContext.class));
		assertEquals("12", proc.toString());
		
		// no indent should run of course
		interp.evalLine("W 1");
		assertEquals("1", proc.toString());
		
		// first line should run, not second line
		reset(interp);
		interp.evalLine("W 1\n. W 2");
		assertEquals("1", proc.toString());
		verify(interp).visitLines(any(LinesContext.class));
		verify(interp, times(2)).visitLine(any(LineContext.class));
	}
	
	@Test
	public void testInvokeSystemFunctions() {
		
		// multiple java methods mapped to $P, $PIECE, should pick one based on available params
		interp.evalLine("W $P(\"FE FI FO FUM\",\" \",2, 3)");
		verify(interp).visitLines(any(LinesContext.class));
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprFunc(any(ExprFuncContext.class));
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
	public void testSysFunc$S() {
		// TODO: Test $P target
		// TODO: Test $E target
		// TODO: Test any target
	}
	
	@M4JRoutine(name="HELLO")
	public static class MyFirstM4JRoutine {
		
		@M4JEntryPoint(name="SAY")
		public static String hello(String val) {
			return "Hello: " + val;
		}
	}
	
	@Test
	public void testJavaRoutineInvoke() throws IOException {
		runtime.registerRoutine(MyFirstM4JRoutine.class);

		interp.evalLine("W !,$$SAY^HELLO($$UP^XLFSTR(\"Brian\")),!");
		assertEquals("\nHello: BRIAN\n", proc.toString());
	}
	
	@Test
	public void testLoadInvokeRoutine() throws IOException {
		interp.evalLine("S SAY=\"hello \",NAME=\"world\" W $$UP^XLFSTR(SAY_NAME)");
		
		assertEquals("HELLO WORLD", proc.toString());
	}


	public void eval(String line) {
		interp.evalLine(line);
	}
}
