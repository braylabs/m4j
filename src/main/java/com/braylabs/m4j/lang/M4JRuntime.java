package com.braylabs.m4j.lang;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.mvstore.MVStore;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.global.MVar.TreeMVar;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy;
import com.braylabs.m4j.parser.MLine;
import com.braylabs.m4j.parser.MCmd.MParseException;

/** Shared by all MProcesses, generally one per JVM? Maybe one per namespace?
 * Acts as the librarian of global, routines, etc. 
 */
public class M4JRuntime {
	
	// all routines by name and then all callable entry points
	private Map<String, RoutineProxy> routines = new HashMap<>();
	
	// global storage for namespace
	private Map<String, MVar> globals = new HashMap<>();
	private MVStore mvstore;
	
	// running threads/processes
	private AtomicInteger procID = new AtomicInteger();
	private Map<Integer,M4JProcess> procs = new HashMap<>();
	private ThreadGroup group = new ThreadGroup("M4J-PROCS");


	public M4JRuntime() {
	}
	
	public M4JRuntime(MVStore store) {
		this.mvstore = store;
	}
	
	public void registerRoutine(RoutineProxy routine) {
		
		routines.put(routine.getName(), routine);
		for (String name : routine.getEntryPointNames()) {
			routines.put(name + "^" + routine.getName(), routine);
		}
	}
	
	public void registerRoutine(Class<?> clazz) {
		registerRoutine(new JavaClassProxy(clazz));
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
	
	
	public RoutineProxy getRoutine(String name) {
		return this.routines.get(name);
	}
	
	/** Spawn/Fork new process in new thread */
	public M4JProcess spawnProcess() {
		// create the process
		int id = this.procID.incrementAndGet();
		M4JProcess ret = new M4JProcess(this, id);
		this.procs.put(id, ret);
		
		// launch it
		String name = "M4J-PROC-" + id;
		Thread t = new Thread(group, ret, name);
		t.setDaemon(true);
		t.start();
		
		return ret;
	}

	// subclasses -------------------------------------------------------------

	/**
	 * Represents a user process, typically running in its own thread
	 * TODO: stack-dump initiated from here?
	 * TODO: how does it clean itself up?
	 * TODO: is this adequate for the FORK command?
	 * TODO: Process-private globals (still persisted, but killed at end of process)
	 * -- TODO: maybe consolidate getGLobal()+getLocal() to getVar() and let it parse it?
	 * 
	 * TODO: how to dump all the current vars (W, ZW)?
	 * 
	 * @author brian
	 */
	public static class M4JProcess implements Runnable {
		private PrintStream out = System.out;
		private M4JRuntime runtime;
		private Thread thread;
		private M4JStackItem stack = new M4JStackItem(null, true, "ROOT");
		private int stackLevel = 0;
		private Map<String, MVar> specialVars = new HashMap<>();
		private Map<String, Object> cache = new HashMap<>();
		private String trace;
		
		public M4JProcess(M4JRuntime runtime, int ID) {
			this.runtime = runtime;
			
			// setup special vars
			MVar v = new TreeMVar("$JOB", ID);
			specialVars.put(v.getName(), v);
		}
		
		protected MVar getSpecialVar(String name) {
			if (specialVars.containsKey(name)) {
				return specialVars.get(name);
			} else if (name.equals("$H") || name.equals("$HOROLOG")) {
				return MUMPS.$HOROLOG();
			} else if (name.equals("$STACK")) {
				return new TreeMVar("$STACK", this.stackLevel);
			}
			return null;
		}
		
		public MVar getGlobal(String name) {
			return this.runtime.getGlobal(name);
		}
		
		public MVar getLocal(String name) {
			if (name.startsWith("$")) return getSpecialVar(name);
			return stack.get(name, false);
		}
		
		/** Essentially the NEW command */
		public void push(boolean exclusive, String... names) {
			stackLevel++;
			this.stack = new M4JStackItem(this.stack, exclusive, this.trace); 
			for (String name : names) {
				this.stack.get(name, true);
			}
		}
		
		public void reset(int levels) {
			for (int i=0; i < levels; i++) this.stack = this.stack.parent;
		}
		
		public RoutineProxy getRoutine(String name) {
			return this.runtime.getRoutine(name);
		}
		
		public M4JRuntime getRuntime() {
			return runtime;
		}
		
		public <T> T setProcessCache(String key, T val) {
			this.cache.put(key, val);
			return val;
		}
		
		public <T> T getProcessCache(String key, Class<T> clazz) {
			return (T) this.cache.get(key);
		}
		
		/** Typically something like: FOO^ROUTINE+3, etc. */
		public void setExecTrace(String trace) {
			this.trace = trace;
		}
		
		public void setOutputStream(OutputStream out) {
			this.out = new PrintStream(out);
		}
		
		public PrintStream getOutputStream() {
			return this.out;
		}
		
		@Override
		public void run() {
			this.thread = Thread.currentThread();
			// TODO: What should it do first?
		}
		
		public Thread getThread() {
			return thread;
		}

		public String stackDump() {
			StringBuffer sb = new StringBuffer();
			M4JStackItem x = stack;
			while (x != null) {
				sb.append(x.getContext()); sb.append("\n");
				x = x.getParent();
			}
			return sb.toString();
		}
		
		/**
		 * Convenience method for evaluating a single M line
		 * @throws MParseException 
		 */
		public Object eval(String mline) throws MParseException {
			MLine line = new MLine(mline);
			return line.eval(this, null);
		}
		
		/**
		 * Execution stack (in a routine, etc.), intended to be a lightweight subclass of process.
		 * 
		 * Primary mechanism for implementing NEW command variable/stack.
		 * @author brian
		 */
		public class M4JStackItem {
			
			private M4JStackItem parent;
			private Map<String, MVar> locals;
			private boolean exclusive;
			private String ctx;
			
			public M4JStackItem(M4JStackItem parent, boolean exclusive, String ctx) {
				this.parent = parent;
				this.ctx = ctx;
				this.exclusive = exclusive;
			}
			
			public String getContext() {
				return ctx;
			}

			public M4JStackItem getParent() {
				return this.parent;
			}

			public MVar get(String name, boolean newd) {
				// if defined locally, return it
				MVar ret = (locals != null) ? locals.get(name) : null;
				if (ret != null) return ret;
				
				// if newd or exclusive mode, create var here
				if (newd || exclusive) {
					// lazy init
					if (locals == null) locals = new HashMap<>();
					locals.put(name, ret = new TreeMVar(name));
					return ret;
				} else {
					// go up the stack
					return parent.get(name, false);
				}
			}
			
			public MVar kill(String name) {
				if (locals != null && locals.containsKey(name)) {
					return locals.remove(name);
				}
				return null;
			}
			
			public MVar newd(String name) {
				if (locals == null) {
					locals = new HashMap<>();
				}
				MVar ret = new MVar.TreeMVar(name);
				locals.put(name,ret);
				return ret;
			}
		}


	}
	
}