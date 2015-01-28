package com.braylabs.m4j.parser;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.Test;

import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;

public class MInterpreterTests {
	
	private MInterpreter interp;
	private M4JProcess proc;
	
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
	public void before() {
		proc = new TestMContext();
		interp = new MInterpreter(proc);
	}
		

	@Test
	public void first() {
		String m = " S FOO=\"Hello\",BAR=\"World\" W !,FOO_\" \"_BAR,1+1,!\n";

		// evaluate the line
		interp.evalLine(m);
		
		// validate check its output stream
		assertEquals("\nHello World2\n", proc.toString());
	}
	
	@Test
	public void second() {
		String m = " S FOO(\"bar\")=\"hello\",FOO(\"baz\")=\"world!\" I FOO(\"bar\")=\"hello\" W !,FOO(\"bar\")_\" \"_FOO(\"baz\"),! ; should write hello world";

		// evaluate the line
		interp.evalLine(m);
		
		assertEquals("\nhello world!\n", proc.toString());
	}
}
