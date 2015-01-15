package gov.va.cpe.vpr.m4j.mparser;

import gov.va.cpe.vpr.m4j.mmap.MMap;
import gov.va.cpe.vpr.m4j.mmap.MMap.LocalMVar;
import gov.va.cpe.vpr.m4j.mmap.MMap.MVStoreMMap;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.h2.mvstore.MVStore;

// TODO: Rename to MProcess?
public class MContext {
	private PrintStream out = System.out;
	private Map<String, MMap> globals = new HashMap<String, MMap>();
	private Map<String, MMap> locals = new HashMap<String, MMap>();
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
	
	public MMap getGlobal(String name) {
		if (!globals.containsKey(name)) {
			globals.put(name, new MVStoreMMap(mvstore, name));
		}
		return globals.get(name);
	}
	
	public MMap getLocal(String name) {
		if (!locals.containsKey(name)) {
			locals.put(name, new LocalMVar(name));
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
