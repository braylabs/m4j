package com.braylabs.m4j.lang;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import jline.console.ConsoleReader;
import jline.console.completer.Completer;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.parser.MCmd;
import com.braylabs.m4j.parser.MInterpreter;
import com.braylabs.m4j.parser.MUMPSLexer;

public class M4JShell {
	
	public static void main(String[] args) throws IOException {
		// process command line args

		// setup shell instance
		M4JRuntime runtime = new M4JRuntime();
		M4JProcess proc = new M4JProcess(runtime, 0);
		MInterpreter interp = new MInterpreter(proc);
		
		ConsoleReader reader = new ConsoleReader();
		reader.addCompleter(new LocalsCompleter(proc));
		
		reader.print("Welcome to M4J console, type 'H' to quit\n");
        reader.setPrompt("\n\u001B[32mM4J\u001B[0m> ");
        String line = null;
        while ((line = reader.readLine()) != null) {
    		// interpret the line
    		try {
    			Object ret = interp.evalLine(line);
    			if (ret == MCmd.MCmdQ.HALT) {
    				break;
    			}
    			
    		} catch (Exception ex) {
    			System.err.println("Unable to interpret line: " + line);
    			ex.printStackTrace();
    		}
        }
	}
	
	private static class LocalsCompleter implements Completer {
		private M4JProcess proc;
		public LocalsCompleter(M4JProcess proc) {
			this.proc = proc;
		}
		@Override
		public int complete(String buffer, int cursor, List<CharSequence> candidates) {
			// get the last token
			ANTLRInputStream input = new ANTLRInputStream(buffer);
			MUMPSLexer lexer = new MUMPSLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			tokens.consume();

			int ret = 0;
			if (tokens.size() > 0) {
				Token last = tokens.get(tokens.size() - 1);
				String txt = last.getText();

				// lookup as a local or global var
				Iterator<String> itr = (txt.startsWith("^")) ? proc.getRuntime().listGlobals() : proc.listLocals();
				while (itr.hasNext() && candidates.size() < 10) {
					String item = itr.next();
					if (item.startsWith(txt)) {
						candidates.add(item);
						ret = last.getCharPositionInLine();
					}
				}
			}

			return ret;
		}
	}
}
