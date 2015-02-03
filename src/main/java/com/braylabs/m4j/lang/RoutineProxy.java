package com.braylabs.m4j.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;

public interface RoutineProxy {
	public String getName();
	public Set<String> getEntryPointNames();
	public Object call(String name, String entrypoint, M4JProcess proc, Object... params) throws Exception;
	
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
		private Map<String, Method> eps = new HashMap<>();
		
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
			
			// loop through methods and get entry points
			for (Method m : clazz.getMethods()) {
				M4JEntryPoint ep = m.getAnnotation(M4JEntryPoint.class);
				if (ep != null) {
					for (String name : ep.name()) eps.put(name, m);
				} else {
					eps.put(m.getName(), m);
				}
			}
			
			// validate
			if (eps.isEmpty()) {
				throw new IllegalArgumentException("No entrypoints marked with M4JEntryPoint routine");
			}
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
		public Object call(String name, String entrypoint, M4JProcess proc, Object... params) throws Exception {
			// if no instance already exists for proc, create one
			Object inst = proc.getProcessCache(clazz.getName(), Object.class);
			if (inst == null) {
				inst = proc.setProcessCache(clazz.getName(), inst = clazz.newInstance());
			}
			
			// invoke
			return this.eps.get(entrypoint).invoke(inst, params);
		}
		
	}
}