package gov.va.cpe.vpr.m4j.lang;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.lang.RoutineProxy.JavaClassProxy.M4JEntryPoint;
import gov.va.cpe.vpr.m4j.lang.RoutineProxy.JavaClassProxy.M4JRoutine;
import gov.va.cpe.vpr.m4j.parser.MCmd.MParseException;
import gov.va.cpe.vpr.m4j.parser.MLine;
import gov.va.cpe.vpr.m4j.parser.MRoutine;
import gov.va.cpe.vpr.m4j.parser.MToken.MLineItem;
import gov.va.cpe.vpr.m4j.parser.ParserTests;
import gov.va.cpe.vpr.m4j.parser.ParserTests.TestMContext;

import org.junit.Before;
import org.junit.Test;

public class MRuntimeTests {
	
	private TestMContext ctx;
	private M4JRuntime runtime;

	@Before
	public void before() throws URISyntaxException, IOException {
		runtime = new M4JRuntime();
		runtime.registerRoutine(MyFirstM4JRoutine.class);
		File f = new File(MRuntimeTests.class.getResource("XLFSTR.int").toURI());
		runtime.registerRoutine(MRoutine.parseFromFile(f));
		ctx = new ParserTests.TestMContext(runtime);
	}

	@M4JRoutine(name="HELLO")
	public static class MyFirstM4JRoutine {
		
		@M4JEntryPoint(name="SAY")
		public String hello(String val) {
			return "Hello: " + val;
		}
	}
	
	@Test
	public void testJavaRoutineInvoke() throws MParseException {
		ctx.eval("W !,SAY^HELLO(\"Brian Bray\"),!");
		assertEquals("\nHello: Brian Bray\n", ctx.toString());
	}
	
	@Test
	public void testMRoutineInvoke() throws FileNotFoundException, IOException, URISyntaxException, MParseException {
		// individual lines
//		ctx.eval("INVERT(X) ;");
//		ctx.eval(" N %,%1 S %=\"\" F %1=$L(X):-1:1 S %=%_$E(X,%1)");
//		ctx.eval(" Q %");
		 
		// now try it as a routine
		ctx.eval(" W INVERT^XLFSTR(\"ABC\")");
		assertEquals("CBA", ctx.toString());
	}
	
}
