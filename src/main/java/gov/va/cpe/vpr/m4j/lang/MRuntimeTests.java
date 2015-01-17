package gov.va.cpe.vpr.m4j.lang;

import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.lang.RoutineProxy.JavaClassProxy.M4JEntryPoint;
import gov.va.cpe.vpr.m4j.lang.RoutineProxy.JavaClassProxy.M4JRoutine;
import gov.va.cpe.vpr.m4j.parser.MLine;

import org.junit.Test;

public class MRuntimeTests {

	@M4JRoutine(name="HELLO")
	public static class MyFirstM4JRoutine {
		
		@M4JEntryPoint(name="SAY")
		public String hello(String val) {
			return "Hello: " + val;
		}
	}
	
	@Test
	public void test() {
		// here is what I'm working towards
		
		M4JRuntime runtime = new M4JRuntime();
		runtime.registerRoutine(MyFirstM4JRoutine.class);
		M4JProcess proc = new M4JProcess(runtime);
		
		MLine.eval("W SAY^HELLO(\"Brian\")", proc);
	}
	
	
}
