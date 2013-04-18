package gov.va.cpe.vpr.m4j.mparser;

import gov.va.cpe.vpr.m4j.HashMMap;
import gov.va.cpe.vpr.m4j.MMap;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class MContext {
	private PrintStream out = System.out;
	private Map<String, MMap> globals = new HashMap<String, MMap>();
	private Map<String, MMap> locals = new HashMap<String, MMap>();
	
	public MMap getGlobal(String name) {
		if (!globals.containsKey(name)) {
			globals.put(name, new HashMMap());
		}
		return globals.get(name);
	}
	
	public MMap getLocal(String name) {
		if (!locals.containsKey(name)) {
			locals.put(name, new HashMMap());
		}
		return locals.get(name);
	}

	
	public void setOutputStream(OutputStream out) {
		this.out = new PrintStream(out);
	}
	
	public PrintStream getOutputStream() {
		return this.out;
	}
}
