package gov.va.cpe.vpr.m4j.lang;

import gov.va.cpe.vpr.m4j.global.MVar;
import gov.va.cpe.vpr.m4j.global.MVar.TreeMVar;
import gov.va.cpe.vpr.m4j.lang.RoutineProxy.JavaClassProxy;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.h2.mvstore.MVStore;

/** Shared by all MProcesses, generally one per JVM? Maybe one per namespace?
 * Acts as the librarian of global, routines, etc. 
 */
public class M4JRuntime {
	
	// all routines by name and then all callable entry points
	private Map<String, RoutineProxy> routines = new HashMap<>();
	
	// global storage for namespace
	private Map<String, MVar> globals = new HashMap<>();
	private MVStore mvstore;

	public M4JRuntime() {
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
		private String trace;
		
		public M4JProcess(M4JRuntime runtime) {
			this.runtime = runtime;
			init();
		}
		
		protected void init() {
			// setup special vars
			MVar v = new TreeMVar("$JOB", 1);
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
		
		/**
		 * TODO: this isn't right... non nude variable in a subroutine will get killed so you can't pass back values
		 * @param name
		 * @return
		 */
		public MVar getLocal(String name) {
			if (name.startsWith("$")) return getSpecialVar(name);
			return stack.get(name);
		}
		
		public RoutineProxy getRoutine(String name) {
			return this.runtime.getRoutine(name);
		}
		
		public M4JRuntime getRuntime() {
			return runtime;
		}
		
		/** Typically something like: FOO^ROUTINE+3, etc. */
		public void setExecTrace(String trace) {
			this.trace = trace;
		}
		
		public M4JStackItem push() {
			return push(false);
		}
		
		public M4JStackItem push(boolean exclusive) {
			stackLevel++;
			return this.stack = new M4JStackItem(this.stack, exclusive, this.trace); 
		}
		
		public M4JStackItem pop() {
			stackLevel--;
			return this.stack = this.stack.getParent();
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

			public MVar get(String name) {
				// lazy initialize map
				if (locals == null) {
					locals = new HashMap<>();
				}
				
				// if defined locally, return it
				MVar ret = locals.get(name);
				if (ret == null && exclusive) {
					// in exclusive mode, ignore parent, create var here
					locals.put(name, ret = new TreeMVar(name));
				} else if (ret == null) {
					// go up the stack
					ret = parent.get(name);
				}
				
				return ret;
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
