package com.braylabs.m4j.lang;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.parser.MInterpreter;
import com.braylabs.m4j.parser.MUMPSParser;
import com.braylabs.m4j.parser.MUMPSParser.FileContext;
import com.braylabs.m4j.parser.MUMPSParser.LinesContext;
import com.braylabs.m4j.parser.MUMPSParser.RoutineLineContext;

public interface RoutineProxy {
	public String getName();
	public Set<String> getEntryPointNames();
	public String getRoutineLine(int idx) throws IOException;
	public URI getRoutineURI() throws IOException;
	public Object call(String entrypoint, M4JProcess proc, Object... params) throws Exception;
	
	public class MInterpRoutineProxy implements RoutineProxy {
		
		private MUMPSParser parser;
		private FileContext fileContext;
		private String name;
		private Set<String> eps;
		private File file;

		public MInterpRoutineProxy(File f) throws IOException {
			this(new FileReader(f));
			this.file = f;
		}
		
		public MInterpRoutineProxy(Reader r) throws IOException {
			init(r);
		}
		
		private void init(Reader r) throws IOException {
			this.parser = MInterpreter.parse(r);
			this.fileContext = parser.file();
			
			// name is equal to the name of the first entrypoint
			this.name = fileContext.routineLine(0).entryPoint().ID().getText();
			
			// quick loop through lines to discover all entryPoint lines
			Set<String> eps = new HashSet<>();
			for (RoutineLineContext line : fileContext.routineLine()) {
				if (line.entryPoint() != null) {
					eps.add(line.entryPoint().ID().getText());
				}
			}
			this.eps = Collections.unmodifiableSet(eps);
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public Set<String> getEntryPointNames() {
			return eps;
		}

		@Override
		public Object call(String entrypoint, M4JProcess proc, Object... params) throws Exception {
			return proc.getInterpreter().evalRoutine(this.fileContext, entrypoint, params);
		}

		@Override
		public URI getRoutineURI() {
			return this.file.toURI();
		}
		
		
		@Override
		public String getRoutineLine(int idx) throws IOException {
			LineNumberReader fr = new LineNumberReader(new FileReader(this.file));
			String line = fr.readLine();
			while (line != null && fr.getLineNumber() < idx) {
				line = fr.readLine();
			}
			fr.close();
			return line;
		}
		
	}
	
	/**
	 * Stub to create one instance per MProcess 
	 * @author brian
	 */
	public class JavaClassProxy implements RoutineProxy {
		
		/** Defines a java class that can be invoked as routine, generally must have a special constructor */
		@Target(ElementType.TYPE)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface M4JRoutine {
			String name();
		}
		
		/** Defines a method that is exposed as a routine entry point */
		@Target(ElementType.METHOD)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface M4JEntryPoint {
			String[] name();
		}
		
		
		/** Services are instantiated once per MRuntime and shared by all MProcesses, 
		 * generally meaning they should be thread safe
		 */
		@Target(ElementType.TYPE)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface M4JService {
			
		}
		
		private Class<?> clazz;
		private String name;
		
		// each entry point can have multiple methods with different args
		private Map<String, List<Method>> eps = new HashMap<>();
		
		public JavaClassProxy(Class<?> clazz) {
			this(clazz, null);
		}
		
		public JavaClassProxy(Class<?> clazz, String name) {
			this.clazz = clazz;
			this.name = name;
			init();
		}
		
		private void init() {
			// if routine name specified as annotation, use it
			M4JRoutine m4jname = clazz.getAnnotation(M4JRoutine.class);
			if (m4jname != null) {
				this.name = m4jname.name();
			}
			
			// otherwise default name is last part of class name
			if (this.name == null) {
				// backup is to use the simple class name
				String[] parts = clazz.getName().split("\\.");
				this.name = parts[parts.length-1];
			}
			
			// loop through methods and get entry points, only static methods tagged as entry points are available to M4J
			for (Method m : clazz.getMethods()) {
				if (!Modifier.isStatic(m.getModifiers())) {
					continue; // only static methods for now
				}
				
				// then any names specified by an annotation
				M4JEntryPoint ep = m.getAnnotation(M4JEntryPoint.class);
				if (ep != null) {
					for (String name : ep.name()) registerMethod(name, m);
				}
			}
			
			// validate
			if (eps.isEmpty()) {
				throw new IllegalArgumentException("No entrypoints marked with M4JEntryPoint routine");
			}
		}
		
		private void registerMethod(String ep, Method method) {
			// Initialize the list/map if needed
			if (!eps.containsKey(ep)) {
				eps.put(ep, new ArrayList<Method>());
			}
			
			// validate the argument types are ones we can handle
			for (Class<?> c : method.getParameterTypes()) {
				if (c == String.class | c == int.class) {
					continue;  // string and number literals are ok
				} else if (c.isAssignableFrom(MVar.class) || c.isAssignableFrom(M4JProcess.class)) {
					continue;  // MVar and M4jProcesses are ok as well.
				} else {
					// otherwise we don't know how to handle it
					throw new IllegalArgumentException("Registered java routines/methods cannot handle method param type: " + c.getName());
				}
			}
			
			// add it
			eps.get(ep).add(method);
		}
		
		@Override
		public String getName() {
			return this.name;
		}
		
		@Override
		public Set<String> getEntryPointNames() {
			return this.eps.keySet();
		}
		

		@Override
		public Object call(String entrypoint, M4JProcess proc, Object... params) throws Exception {
			/*
			// if no instance already exists for proc, create one
			Object inst = proc.getProcessCache(clazz.getName(), Object.class);
			if (inst == null) {
				inst = proc.setProcessCache(clazz.getName(), inst = clazz.newInstance());
			}
			*/
			
			// resolve the possible method targets
			List<Method> methods = eps.get(entrypoint);
			if (methods == null) {
				throw new IllegalArgumentException("Unknown entrypoint: " + entrypoint + " in java routine: " + this.name);
			}
			
			// find one of the method signatures that we can map/resolve out parameters to
			for (Method m : methods) {
				Object[] resolvedParams = resolveParams(m, proc, params);
				if (resolvedParams == null) continue; // unable to resolve
				
				// we have a match, invoke and return
				return m.invoke(null, resolvedParams);
			}
			
			// if we got here, we couldn't find a match, throw an error
			throw new IllegalArgumentException("Unable to resolve paramters for java entrypoint: " + entrypoint);
		}

		/** do necessary parameter translation/insertion prior to invoking the method, return null if we cannot do it 
		 * TODO: This is going to get complicated quickly
		 * - When should an undefined or unresolved variable throw an error?
		 * - what are valid conversions?
		 * - look into how spring does this kind of stuff for some inspiration?
		 * 
		 * */
		private Object[] resolveParams(Method m, M4JProcess proc, Object[] inParams) {
			int paramCount = m.getParameterTypes().length;
			List<Object> params = new ArrayList<>(Arrays.asList(inParams));
			
			int i=0;
			Object[] ret = new Object[paramCount];
			for (Class<?> clazz : m.getParameterTypes()) {
				boolean hasMore = !params.isEmpty();
				if (clazz == M4JProcess.class) {
					ret[i] = proc;
				} else if (clazz == MVar.class && hasMore) {
					ret[i] = params.remove(0);
				} else if ((clazz == String.class || clazz == int.class) && hasMore) {
					
					// conversion from MVal or MVar to literal value
					
					
					
					
					// if input parameter is a MVar, it must be defined
					Object param = params.remove(0);
					if (param instanceof MVar) {
						MVar paramVar = (MVar) param;
						if (!paramVar.isDefined()) throw new IllegalArgumentException("Cannot convert undefined variable to a literal value: " + paramVar.getFullName());
						if (clazz == String.class) {
							ret[i] = paramVar.val().toString();
						} else {
							ret[i] = paramVar.valInt();
						}
					} else if (param instanceof MVal) {
						MVal paramVal = (MVal) param;
						if (clazz == String.class) {
							ret[i] = paramVal.toString();
						} else {
							ret[i] = paramVal.toNumber().intValue();
						}
					} else if (param instanceof String) {
						ret[i] = param;
					} else if (param instanceof Integer) {
						ret[i] = param;
					} else {
						// what is this?
						throw new IllegalArgumentException("Unable to resolve paramter, unknown type/class: " + param.getClass());
					}
				} else if (!hasMore) {
					// missmatch in # of arguments, skip and try to match a different method signature later
					return null;
				} else {
					throw new IllegalArgumentException("Not a valid/recognized/permitted parameter type for M4J: " + clazz);
				}
				i++;
			}
			
			// if stack is not empty, we have a missmatch, return null and see if another method matches
			if (!params.isEmpty()) {
				return null;
			}
			
			// success
			return ret;
		}

		@Override
		public String getRoutineLine(int idx) {
			return null;
		}
		
		@Override
		public URI getRoutineURI() throws IOException {
			return null;
		}
		
	}
}