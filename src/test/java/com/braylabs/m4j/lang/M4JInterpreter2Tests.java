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
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JInterpreter2;
import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.MUMPS2Lexer;
import com.braylabs.m4j.lang.MUMPS2Parser;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdContext;
import com.braylabs.m4j.lang.MUMPS2Parser.CmdPostCondContext;
import com.braylabs.m4j.lang.MUMPS2Parser.EntryPointContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprBinaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprFuncContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprGroupContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprIndrExprContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprIndrVarContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprLiteralContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprMatchContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprRefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprUnaryContext;
import com.braylabs.m4j.lang.MUMPS2Parser.ExprVarContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LineContext;
import com.braylabs.m4j.lang.MUMPS2Parser.LinesContext;
import com.braylabs.m4j.lang.MUMPS2Parser.RefContext;
import com.braylabs.m4j.lang.MUMPS2Parser.VarContext;
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
	}
	
	@Test
	public void testLiteralExpr() {
		eval("W 1");
		assertEquals("1", proc.toString());
		
		eval("W \"FOO\"");
		assertEquals("FOO", proc.toString());
		
		eval("W !,1,2,3,!");
		assertEquals("\n123\n", proc.toString());
		
		eval("W ?10,10,!");
		assertEquals("          10\n", proc.toString());
		
		eval("W \"12 monkeys\"");
		assertEquals("12 monkeys", proc.toString());

		eval("W +\"12 monkeys\"");
		assertEquals("12", proc.toString());

		// several expressions the parser has choked on before
		eval("S JSON(.5)=10 W JSON(.5)");
		assertEquals("10", proc.toString());
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
		eval("W --1");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp, times(2)).visitExprUnary(any(ExprUnaryContext.class));
		assertEquals("1", proc.toString());
		
		// chained unary operators
		eval("W -(+(--1))");
		assertEquals("-1", proc.toString());
	}
	
	@Test
	public void testVar() {
		// setup values to test
		eval("S ^FOO=0,^FOO(1,2,3)=3");
		eval("S FOO=-1,FOO(1,2,3)=-3");
		
		// subscripted var/global
		eval("W ^FOO(1,2,3)");
		assertEquals("3", proc.toString());
		eval("W ^FOO");
		assertEquals("0", proc.toString());
		eval("W FOO");
		assertEquals("-1", proc.toString());
		
		// special vars
		eval("W $FOO");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprVar(any(ExprVarContext.class));
		verify(interp).visitVar(any(VarContext.class));

		// Naked global (FOO)
		eval("W ^(1,2,3)");
		assertEquals("3", proc.toString());
		
		// should parse
		eval("W .FOO");
		eval("W .FOO(1,2,3)");
	}
	
	@Test
	public void testCMDDoRoutine() {
		// this DO should run, but not actually return anything
		eval("D UP^XLFSTR(\"fOO\")");
		assertEquals("", proc.toString());
		
		// DO command
		ArgumentCaptor<CmdContext> argument = ArgumentCaptor.forClass(CmdContext.class);
		verify(interp).visitCmd(argument.capture());
		assertEquals("D", argument.getValue().ID().getText());

		// QINDEX^VPRJPQ call
		ArgumentCaptor<RefContext> ref = ArgumentCaptor.forClass(RefContext.class);
		verify(interp).visitRef(ref.capture());
		assertEquals("XLFSTR", ref.getValue().routine.getText());
		assertEquals("UP", ref.getValue().ep.getText());
		
		// TODO: refs can skip some arguments
		eval("D QINDEX^VPRJPQ(VPRJTPID,\"med-ingredient-name\",\"METFOR*\",,,\"uid\")");
		
		// TODO: how to test shortcut definition (jump to tag in current routine)
	}
	
	@Test
	public void testCMDDoLoop() {
		// do as a loop
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
		// setup data
		eval("S X=\"FOO\",FOO=10,FOO(\"FOO\")=11");
		
		// simple
		eval("W @X");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprIndrVar(any(ExprIndrVarContext.class));
		verify(interp, times(2)).resolveVar(any(String.class), any(MVal[].class));
		assertEquals("10", proc.toString());

		// subscripted
		eval("W @X@(X)");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprIndrVar(any(ExprIndrVarContext.class));
		verify(interp, times(3)).resolveVar(any(String.class), any(MVal[].class));
		assertEquals("11", proc.toString());
		
		// found issue
		eval("S X=\"FOO\",FOO=\"X\" W @X");
		assertEquals("X", proc.toString());
	}
	
	@Test
	public void testPostConditional() {
		// expression evaluates
		eval("W:1 1+2");
		assertEquals("3", proc.toString());
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp, times(3)).visitExprLiteral(any(ExprLiteralContext.class));

		// expression never evaluates
		eval("W:0 1+2");
		assertEquals("", proc.toString());
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprLiteral(any(ExprLiteralContext.class));
		
		// multiple post conditional expressions
		eval("W:(1) 100");
		assertEquals("100", proc.toString());

	}
	
	@Test
	public void test$SELECT() {
		
		eval("W $S(1=2:\"ONE\",1=3:2,1:1+2)");
		assertEquals("3", proc.toString());
		
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprFunc(any(ExprFuncContext.class));
		verify(interp, times(3)).visitExprBinary(any(ExprBinaryContext.class));
		verify(interp, times(7)).visitExprLiteral(any(ExprLiteralContext.class));
		
		// TODO: How to test that the value of the first two options did not get evaluated?
		// TODO: lots of alternate expression/structure needed here
		// TODO: lots of error testing needed here.
	}
	
	@Test
	public void testHelloWorld() {
		eval("S FOO=\"Hello\",BAR=\"World\" W !,FOO_\" \"_BAR,1+1,!");
		
		// validate check its output stream
		assertEquals("\nHello World2\n", proc.toString());
	}
	
	@Test
	public void testCMDWrite() {
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
		eval("W !?10,10,!");
		assertEquals("\n          10\n", proc.toString());
	}
	
	@Test
	public void testCMDIf() {
		String m = " S FOO(\"bar\")=\"hello\",FOO(\"baz\")=\"world!\" I FOO(\"bar\")=\"hello\" W !,FOO(\"bar\")_\" \"_FOO(\"baz\"),! ; should write hello world";

		// evaluate the line
		interp.evalLine(m);
		assertEquals("\nhello world!\n", proc.toString());
		
		// Evaluate line where IF is false, should abort whole line
		interp.evalLine("I 1>10 W \"truth\" W \"more truth\"");
		assertEquals("", proc.toString());
		
	}
	
	@Test
	public void testCMDFor() {
		eval("F I=0:1 W I Q:I=5");
		assertEquals("012345", proc.toString());

		eval("F I=0:1:10 W I");
		assertEquals("012345678910", proc.toString());
	}
	
	@Test
	public void testCMDGo() {
		// TODO: how to test w/o specifying routine name?
		eval("G UP^XLFSTR:1=1,LOW^XLFSTR:1=2,UP^XLFSTR");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
	}
	
	@Test
	public void testCMDQuit() {
		// having problems with this:
		// should not evaluate the variable
		eval("S I=5 Q:I>5");
		assertEquals("", proc.toString());
		
		ArgumentCaptor<CmdContext> argument = ArgumentCaptor.forClass(CmdContext.class);
		verify(interp, times(2)).visitCmd(argument.capture());
		assertEquals("Q", argument.getValue().ID().getText());
	}
	
	@Test
	public void test$TEXT() {
		// can have strange syntax: TAG+N^ROUTINE
		eval("W $T(REPLACE+1^XLFSTR)");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprFunc(any(ExprFuncContext.class));
		assertEquals(" Q:'$D(IN) \"\" Q:$D(SPEC)'>9 IN N %1,%2,%3,%4,%5,%6,%7,%8", proc.toString());
		
		eval("W $T(REPLACE^XLFSTR)");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprFunc(any(ExprFuncContext.class));
		assertEquals("REPLACE(IN,SPEC) ;See $$REPLACE in MDC minutes.", proc.toString());
		
		eval("W $T(REPLACE+(1+2*3)^XLFSTR)");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprFunc(any(ExprFuncContext.class));
		assertEquals("RE2 Q:$E(%7,%5-%4,%5-1)[\"X\"  S %8(%5-%4)=SPEC(%3)", proc.toString());
		
		// TODO: Test with no routine name, should default to current routine
		// TODO: What happens if you try to get a line from a java-encoded routine?
		// TODO: error conditions in expression?
	}

	
	@Test
	public void testCMDSet() {
		// $P and $E can be target of SET command
		eval("S FOO=\"A,B,C\",$P(FOO,\",\", 2)=\"X\" W FOO");
		assertEquals("A,X,C", proc.toString());
		
		eval("S FOO=\"ABC\",$E(FOO,2)=\"X\" W FOO");
		assertEquals("AXC", proc.toString());
		
		// set several vars at once
		eval("S (A,B,C)=1 W A,B,C");
		assertEquals("111", proc.toString());
		
		// combo of two styles
		eval("S FOO=\"A,B,C\",(A,B,$P(FOO,\",\",1))=1 W FOO");
		assertEquals("1,B,C", proc.toString());

		// longer forms of $P,$E
		eval("S FOO=\"ABC\",$E(FOO,2,3)=\"XX\" W FOO");
		assertEquals("AXX", proc.toString());

		eval("S FOO=\"A,B,C\",$P(FOO,\",\",2,3)=\"X\" W FOO");
		assertEquals("A,X", proc.toString());
		

		// TODO: Test/trace setting global vs local
		// TODO: Test/implement setting special vars works sometimes, fails other times
	}
	
	@Test
	public void testCMDHang() {
		long start = System.currentTimeMillis();
		eval("H 1,1,1");
		assertEquals(3000, System.currentTimeMillis()-start, 300);
		
		// does nothing
		start = System.currentTimeMillis();
		eval("H 0");
		assertEquals(0, System.currentTimeMillis()-start, 300);
		
		// expressions are valid
		eval("H 1+1");
		
		// fractions are ok
		start = System.currentTimeMillis();
		eval("H .1");
		assertEquals(100, System.currentTimeMillis()-start, 10);
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
	public void testPatternMatch() {
		eval("W \"FOO\"?1L");
		verify(interp).visitExprMatch(any(ExprMatchContext.class));
		assertEquals("0", proc.toString());
		
		eval("W \"123-12-1234\"?3N1\"-\"2N1\"-\"4N");
		verify(interp).visitExprMatch(any(ExprMatchContext.class));
		assertEquals("1", proc.toString());
		
		eval("W \"ABC1\"?1A.AN");
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
	public void test$PIECE() {
		
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
	public void test$ORDER() {
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
	public void testSyntaxErrors() {
		
		try {
			eval("W FOO()"); // must be FOO
			fail("exception expected");
		} catch (Exception ex) {
			// expected
		}
		
		try {
			eval("W ^FOO()"); // must be ^FOO
			fail("exception expected");
		} catch (Exception ex) {
			// expected
		}
		
		try {
			eval("W 1+BAR");
			fail("exception expected");
		} catch (Exception ex) {
			// expected
		}
	}
	
	@Test
	public void testRemainingLexerParserIssues() {
		// command syntax/indirection parse errors
		eval("W $T(@TAG+I^@RTN)");
		// TODO: VPRJT.int: Line tags can start with digits!?!
		eval("D @(ROUTINE_\"(.HTTPRSP,.HTTPARGS)\")");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprIndrExpr(any(ExprIndrExprContext.class));
	}
	
	@Test
	public void testKnownInterpreterIssues() {
		// fractional number subtleties
		eval("W -.44");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprUnary(any(ExprUnaryContext.class));
		assertEquals("-.44", proc.toString());
		
		eval("W -0.44");
		verify(interp).visitLine(any(LineContext.class));
		verify(interp).visitCmd(any(CmdContext.class));
		verify(interp).visitExprUnary(any(ExprUnaryContext.class));
		assertEquals("-.44", proc.toString());

		
		// should be error
		try {
			eval("W ^ZZZ");
			fail("exception expected");
		} catch (Exception ex) {
			// expected
		}
	}
	
	@Test
	public void testLoadInvokeRoutine() throws IOException {
		interp.evalLine("S SAY=\"hello \",NAME=\"world\" W $$UP^XLFSTR(SAY_NAME)");
		
		assertEquals("HELLO WORLD", proc.toString());
	}


	public void eval(String line) {
		reset(interp);
		interp.evalLine(line);
	}
}
