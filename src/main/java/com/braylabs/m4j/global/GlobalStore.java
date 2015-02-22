package com.braylabs.m4j.global;

import java.io.Closeable;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.intersys.globals.Connection;
import com.intersys.globals.ConnectionContext;
import com.intersys.globals.GlobalsDirectory;

/**
 * Wrapper around various types of global storage
 * TODO: Add transaction hooks
 * TODO: How to support multiple namespaces
 * TODO: How to support mapping globals to different storage engines/partitions?
 */
public abstract class GlobalStore implements Closeable {

	public abstract void kill(String name);
	@Override
	public abstract void close();
	
	public abstract MVar get(String name);
	public abstract Iterator<String> list();
	
	/** Uses the cacheextreem.jar JNI direct memory connection into InterSystems Cache */
	public static class CacheGlobalStore extends GlobalStore {
		
		private String namespace;
		private Connection conn;
		private GlobalsDirectory dir;

		public CacheGlobalStore() {
			this(null, ConnectionContext.getConnection());
		}
		
		public CacheGlobalStore(String namespace, Connection conn) {
			this.conn = conn;
			this.namespace=namespace;
			
			// Initialize connection if not connected
			if (!this.conn.isConnected()) {
				this.conn.connect();
			}
			
			// if name space is specified, switch to it
			if (namespace != null) {
				conn.setNamespace(namespace);
			}
			
			// get globals index
			this.dir = conn.createGlobalsDirectory();
		}

		@Override
		public void kill(String name) {
			this.conn.createNodeReference(name).kill();
		}

		@Override
		public void close() {
			this.conn.close();
		}

		@Override
		public MVar get(String name) {
			return new MVar.CacheMVar(conn.createNodeReference(name));
		}

		@Override
		public Iterator<String> list() {
			Set<String> ret = new HashSet<>();
			String glob = dir.nextGlobalName();
			while (glob != null && !glob.isEmpty()) {
				ret.add(glob);
				glob = dir.nextGlobalName();
			}
			return ret.iterator();
		}
		
	}
	
	public static class MVGlobalStore extends GlobalStore {

		private MVStore store;
		private MVMap<String, String> metamap;

		/** Initializes default store in temporary directory */
		public MVGlobalStore() {
			this(null);
		}
		
		public MVGlobalStore(MVStore store) {
			this.store = store;
			if (this.store == null) {
		        String tmpdir = System.getProperty("java.io.tmpdir");
		        if (!tmpdir.endsWith(File.separator)) tmpdir += File.separator;
		        File tmpfile = new File(tmpdir, "M4J.globals.data");
		     	this.store = new MVStore.Builder().fileName(tmpfile.getAbsolutePath()).cacheSize(20).open();
			}
			this.metamap = this.store.getMetaMap();
		}
		
		@Override
		public void kill(String name) {
			this.store.openMap(name).removeMap();
		}

		@Override
		public void close() {
			this.store.close();
		}

		@Override
		public MVar get(String name) {
			return new MVar.MVStoreMVar(this.store, name);
		}

		@Override
		public Iterator<String> list() {
			Set<String> ret = new HashSet<>();
			Iterator<String> itr = this.metamap.keyIterator("name.");
			while (itr.hasNext()) {
				String key = itr.next();
				if (key.startsWith("name.")) {
					ret.add(key.replace("name.", "^"));
				} else {
					break; // quit once we get through the name. values
				}
			}
			
			return ret.iterator();
		}
		
	}
}
