package com.braylabs.m4j.lang;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import jline.console.ConsoleReader;
import jline.console.completer.Completer;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.h2.mvstore.MVStore;

import com.braylabs.m4j.global.GlobalStore;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.parser.MInterpreter;
import com.braylabs.m4j.parser.MUMPSLexer;

public class M4JShell {
	
	public static void main(String[] args) throws IOException {
		// declare all the arguments
		OptionParser parser = new OptionParser();
		OptionSpec<String> optCP = parser.acceptsAll(Arrays.asList("cp","classpath"), "Routine classpath")
				.withOptionalArg().ofType(String.class);
		OptionSpec<String> optGlobals = parser.acceptsAll(Arrays.asList("globals"), "Globals storage: CACHE or MVSTORE")
				.withOptionalArg().ofType(String.class);
		OptionSpec<String> optGlobalsDir = parser.acceptsAll(Arrays.asList("globals.dir"), "Globals storage directory (MVSTORE only)")
				.withOptionalArg().ofType(String.class);
		OptionSpec<String> optDebug = parser.acceptsAll(Arrays.asList("d","debug"), "Show debug info").withOptionalArg().ofType(String.class);
		OptionSpec<Void> optHelp = parser.acceptsAll(Arrays.asList("h", "?", "help"), "Show Help").forHelp();
	     
		// parse the arguments
		OptionSet options = null;
		try {
			options = parser.parse(args);
			if (options.hasArgument(optHelp)) {
				parser.printHelpOn(System.out);
				return;
			}
		} catch (OptionException e) {
			e.printStackTrace();
			parser.printHelpOn(System.out);
			return;
		}
		
		// setup global storage based on supplied parameters
		GlobalStore store = null;
		if (options.hasArgument(optGlobals)) {
			if (options.valueOf(optGlobals).equalsIgnoreCase("CACHE")) {
				store = new GlobalStore.CacheGlobalStore();
			} else if (options.hasArgument(optGlobalsDir)) {
				MVStore mvstore = new MVStore.Builder().fileName(options.valueOf(optGlobalsDir)).cacheSize(20).open();
				store = new GlobalStore.MVGlobalStore(mvstore);
			} else {
				store = new GlobalStore.MVGlobalStore();
			}
		}
		
		// setup shell instance
		M4JRuntime runtime = new M4JRuntime(store);
		M4JProcess proc = new M4JProcess(runtime, 0);
		M4JInterpreter2 interp = new M4JInterpreter2(proc);
		proc.getSpecialVar("$ROUTINE").set("<CONSOLE>");
		interp.setDebugMode(options.hasArgument(optDebug));
		
		// setup console and prompt
		ConsoleReader reader = new ConsoleReader();
		reader.setExpandEvents(false);
		reader.addCompleter(new RuntimeCompleter(proc));
		reader.print("Welcome to M4J console, type 'H' to quit\n");
        reader.setPrompt("\n\u001B[32mM4J\u001B[0m> ");
        
        // load any routines/classes
        loadRoutines(options.valueOf(optCP), runtime);
        
        String line = null;
        while ((line = reader.readLine()) != null) {
    		// interpret the line
        	if (line.equals("")) continue; // do nothing
    		try {
    			Object ret = interp.evalLine(line);
    			if (ret == MInterpreter.MFlowControl.HALT) {
    				break;
    			}
    			
    		} catch (Exception ex) {
    			System.err.println("Error interpreting line: " + line);
    			ex.printStackTrace();
    		}
        }

        runtime.close();
	}
	
	private static void loadRoutines(String classPath, M4JRuntime runtime) throws IOException {
		if (classPath == null) return;
		
        StringTokenizer st = new StringTokenizer(classPath, File.pathSeparator);
        while (st.hasMoreTokens()) {
        	String tok = st.nextToken();
        	File f = new File(tok);
        	
        	// if its a routine file (.m, .int), load via RoutineProxy
        	if (MCompiler.M_FILTER.accept(f)) {
        		System.out.println("Loading/Interpreting: " + f);
        		runtime.registerRoutine(new RoutineProxy.MInterpRoutineProxy(f));
        	}
        	
        	// if its a jar, load via URLClassLoader
        }
	}
	
	
	private static class RuntimeCompleter implements Completer {
		private M4JProcess proc;
		public RuntimeCompleter(M4JProcess proc) {
			this.proc = proc;
		}
		@Override
		public int complete(String buffer, int cursor, List<CharSequence> candidates) {
			// get the last token
			ANTLRInputStream input = new ANTLRInputStream(buffer);
			MUMPSLexer lexer = new MUMPSLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			tokens.consume();

			// skip if there is nothing to suggest
			int ret = 0;
			if (tokens.size() == 0)  return ret;
			
			// get the last token or two
			Token last = tokens.get(tokens.size() - 1);
			Token prev = tokens.get(tokens.size() - 2);
			String txt = last.getText();
			
			// the lexer seems to ignore the last bit of token sometimes, try to complete it by using the buffer
			txt = buffer.substring(buffer.lastIndexOf(txt));
			
			// is it a routine reference?
			if (txt.startsWith("$$")) {
				String match = txt.substring(2);
				Iterator<String> itr = proc.getRuntime().listRoutines();
				while (itr.hasNext()) {
					String item = itr.next();
					if (item.startsWith(match)) {
						candidates.add("$$" + item);
						ret = last.getCharPositionInLine();
					}
					if (candidates.size() > 10) return ret;
				}
			}
			
			// lookup as a local or global var
			boolean isGlobal = (txt.startsWith("^") || (prev != null && prev.getText().startsWith("^")));
			Iterator<String> itr = (isGlobal) ? proc.getRuntime().listGlobals() : proc.listLocals();
			while (itr.hasNext() && candidates.size() < 10) {
				String item = itr.next();
				if (item.startsWith(txt)) {
					candidates.add(item);
					ret = last.getCharPositionInLine();
				}	
			}

			return ret;
		}
	}
}
