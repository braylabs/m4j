package com.braylabs.m4j.lang;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JEntryPoint;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JRoutine;
import com.braylabs.m4j.parser.MCmd.MParseException;
import com.braylabs.m4j.parser.MLineTests;
import com.braylabs.m4j.parser.MRoutine;

public class MRuntimeTests {
	
	private MLineTests.TestMContext ctx;
	private M4JRuntime runtime;

	@Before
	public void before() throws URISyntaxException, IOException {
		runtime = new M4JRuntime();
		runtime.registerRoutine(MyFirstM4JRoutine.class);
		File f = new File(MRuntimeTests.class.getResource("XLFSTR.int").toURI());
		runtime.registerRoutine(MRoutine.parseFromFile(f));
		ctx = new MLineTests.TestMContext(runtime);
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
