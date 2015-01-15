package gov.va.cpe.vpr.m4j.lang;

import gov.va.cpe.vpr.m4j.global.MVar;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.h2.mvstore.MVStore;

// TODO: Rename to MProcess?
public class MProcess {
	private PrintStream out = System.out;
	private Map<String, MVar> globals = new HashMap<>();
	private Map<String, MVar> locals = new HashMap<>();
	private MVStore mvstore;
	
	public void setStore(MVStore store) {
		this.mvstore = store;
	}
	
	protected MVStore getStore() {
		if (mvstore != null) return mvstore;
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (!tmpdir.endsWith(File.separator)) tmpdir += File.separator;
        File tmpfile = new File(tmpdir, "MContext.data");
     	return mvstore = new MVStore.Builder().fileName(tmpfile.getAbsolutePath()).cacheSize(20).open();
	}
	
	public MVar getGlobal(String name) {
		if (!globals.containsKey(name)) {
			globals.put(name, new MVar.MVStoreMVar(mvstore, name));
		}
		return globals.get(name);
	}
	
	public MVar getLocal(String name) {
		if (!locals.containsKey(name)) {
			locals.put(name, new MVar.TreeMVar(name));
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
