# M4J - MUMPS for JAVA

An attempt at making a MUMPS runtime environment for the java virtual machine (JVM) similar to how other programming languages such as Groovy, Scala, Ruby, Python etc. have utilized the JVM platform

- ANTLR-based Lexer/parser that can interpret and execute M code
- Compiler that can compile M code to JVM byte-code (very early, only 1-2 commands implemented)
- Globals API/proxy
  - Plain java persistence (Using H2's MVStore storage engine)
  - Connecters back to InterSystems Cache (Using cache extreme drivers)
- Runtime environment (~40% of the system commands/functions implemented)
- Shell/console tool including:
  - command history 
  - TAB-expansion of variable and routine names
  - VI/EMACS-style key bindings

## About
- Created by [Brian Bray](https://www.linkedin.com/in/bbray), President/Founder of BrayLabs
- Initially created as an exercise in learning M while working for the VA on the Health Management Platform (HMP).  The original project, not the current eHMP project.
- This is an enhanced version of what was originally published as open source software by the VA to [OSEHRA](http://hdl.handle.net/10909/11049), 
- Questions/comments can be directed to me via email, or I also still lurk on the [Hardhats](https://groups.google.com/forum/#!forum/hardhats) list a bit.

## Prerequisites
- Java (7 or 8)
- Maven
- InterSystems Cache (Optional)
- ANTLR4 Eclipse Plugin (Optional)

## Known issues
- Currently will fail to fetch Cache dependencies, might need to comment them out or install into local maven repo manually
- `CacheGlobalStore` doesn't work on OSX, the cache driver causes core dumps
- There is still some quirky M code that the ANTLR grammar cannot handle
- Not all the commands/system functions are implemented nor are they fully implemented and completely compatible
- Some forms of indirection work, others do not

## Quick start

    git clone https://github.com/braylabs/m4j.git
    cd m4j
    mvn package
    
## Quick Start - Launch M4J Console
    java -jar target/m4j-0.1.jar
    Welcome to M4J console, type 'H' to quit
    
    M4J> S SAY="HELLO",NAME="WORLD"
    M4J> W SAY_" "_NAME
    HELLO WORLD
    M4J>
    
## Quick Start - M4J Compiler
Currently only 1 M routine in `src/test/` compiles.  This should generate a class file that can be utilized by regular java apps.
```
m4jc -package=m4j. -dest=target/m4jc/m4j src/test/java/com/braylabs/m4j/lang/M4JCTST.int
```
    
## Quick Start - Java Code Example
```java
import java.io.File;
import java.io.IOException;

import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.lang.RoutineProxy;
import com.braylabs.m4j.lang.RoutineProxy.MInterpRoutineProxy;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JEntryPoint;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JRoutine;

import static com.braylabs.m4j.lang.MUMPS.*;

public class Demo {
	public static void main(String[] args) throws IOException {
	  // Create a M4J runtime and register 2 routines:
	  // 1) Register an annotated java static method as a M routine
	  // 2) Load/Parse a M-language routine file from disk
		M4JRuntime runtime = new M4JRuntime();
		runtime.registerRoutine(new RoutineProxy.JavaClassProxy(MyFirstM4JRoutine.class));
		runtime.registerRoutine(new MInterpRoutineProxy(new File("src/main/mumps/XLFSTR.int")));
		
		// Create a M4J Process and evaluate a simple line
		M4JProcess proc = new M4JProcess(runtime, 123);
		proc.getInterpreter().evalLine("W !,$$SAY^HELLO($$UP^XLFSTR(\"world\")),!");
	}

	// Register this class as a M routine named 'HELLO'
	@M4JRoutine(name="HELLO")
	public static class MyFirstM4JRoutine {
		
		// With an entry point named 'SAY'
		@M4JEntryPoint(name="SAY")
		public static String hello(String val) {
			return "Hello: " + val;
		}
	}
}
```

