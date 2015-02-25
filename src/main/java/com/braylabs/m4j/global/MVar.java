package com.braylabs.m4j.global;

import java.io.Serializable;
import java.util.Iterator;
import java.util.TreeMap;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.braylabs.m4j.lang.MVal;
import com.braylabs.m4j.parser.MParserUtils;
import com.intersys.globals.Connection;
import com.intersys.globals.NodeReference;

/**
 * TODO:
 * - add a toMap() method/mechanism
 * - add a size() mechanism
 * - in toString(), make it indent values like %G does
 * - both MVar.data implementations are navigable map/map interfaces, can we take advantage of that and have more reuse?
 * - lock for the variable for atomic operations?
 * @author brian
 *
 */
public abstract class MVar {
	
	private String name;
	protected MVarKey path;
	protected MVar root;
	
	/** defines a new root MVar */
	public MVar(String name) {
		this.name = name;
		this.path = new MVarKey();
		this.root = this;
	}
	
	protected MVar(MVar root, MVarKey path) {
		this.path = path;
		this.root = root;
	}
	
	public String getName() {
		// dont need to store the name in each sub object, just the root
		return (this.name == null) ? this.root.name : this.name;
	}
	
	public String getFullName() {
		return getName() + ((path != null) ? path.toString() : "");
	}
	
	public Integer valInt() {
		Object obj = val();
		if (obj == null) return null;
		return MParserUtils.evalNumericValue(obj).intValue();
	}
	
	public String valStr() {
		Object obj = val();
		if (obj == null) return null;
		return obj.toString();
	}

	public Object val() {
		return doGetValue(this.path);
	}
	
	public Object val(Comparable<?>... keys) {
		return get(keys).val();
	}
	
	public MVar get(Comparable<?>... keys) {
		return get(this.path.append(keys));
	}
	
	/* set the nodes value, return the new value */
	public Object set(Object val) {
		return doSetValue(this.path, val);
	}
	
	// abstract methods -------------------------------------------------------

	/* Returns this nodes value, same as get(null) */
	public abstract Object doGetValue(MVarKey key);
	public abstract Object doSetValue(MVarKey key, Object val);
	public abstract Object unset();
	public abstract boolean isDefined();
	public abstract boolean hasDescendents();
	public abstract MVar get(MVarKey key);
	public abstract MVarKey nextKey();
	public abstract MVarKey prevKey();
	protected abstract Iterator<MVarKey> iterator(); 

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
			sb.append(getName());
			if (this.path.size() > 0) path.toString(sb);	
			if (isDefined()) {
			sb.append("=");
			sb.append(val());
		}
		
		
		Iterator<MVarKey> itr = iterator();
		while (itr.hasNext()) {
			MVarKey key = itr.next();
			if (sb.length() > 0) sb.append("\n");
			sb.append(getName());
			sb.append(key);
			sb.append("=");
			sb.append(doGetValue(key));
		}
		return sb.toString();
	}
	
	// map implementation -----------------------------------------------------
	
	// subclasses -------------------------------------------------------------
	
	public static class MVStoreMVar extends MVar {
		private MVMap<MVarKey, Serializable> data;
		private MVStore store;

		public MVStoreMVar(MVStore store, String name) {
			super(name);
			this.store = store;
			this.data = store.openMap(name);
		}
		
		protected MVStoreMVar(MVStoreMVar root, MVarKey path) {
			super(root, path);
			this.store = root.store;
			this.data = root.data;
		}
		
		@Override
		public Object doGetValue(MVarKey key) {
			return this.data.get(key);
		}

		@Override
		public Object doSetValue(MVarKey key, Object val) {
			this.data.put(key, (Serializable) val);
			this.store.commit(); // should it autocommit like this?
			return val;
		}
		
		@Override
		public Object unset() {
			return this.data.remove(this.path);
		}

		@Override
		public boolean isDefined() {
			return this.data.containsKey(path);
		}
		
		@Override
		public boolean hasDescendents() {
			MVarKey next = data.higherKey(this.path);
			if (next == null) return false;
			return (next.before(this.path.append(null)));
		}

		@Override
		public MVar get(MVarKey key) {
			return new MVStoreMVar(( MVStoreMVar) this.root, key);
		}
		
		@Override
		public MVarKey nextKey() {
			MVarKey tmp = this.path.append(null);
			MVarKey next = data.higherKey(tmp);
			if (next == null) return null;
			
			// if next node is at a higher level, return null (no more nodes on same level)
			if (this.path.size() > next.size()) return null;
			
			// if next node is at a lower level, splice it to same level
			return next.splice(this.path.size());
		}
		
		@Override
		public MVarKey prevKey() {
			return data.floorKey(this.path);
		}

		@Override
		protected Iterator<MVarKey> iterator() {
			final MVarKey stopAt = this.path.append(null);
			final Iterator<MVarKey> itr = data.keyIterator(this.path);
			
			return new Iterator<MVarKey>() {
				MVarKey last = null;
				
				@Override
				public boolean hasNext() {
					if (!itr.hasNext()) return false;
					last = itr.next();
					if (!last.before(stopAt)) last = null;
					return last != null;
				}

				@Override
				public MVarKey next() {
					return last;
				}

				@Override
				public void remove() {
					itr.remove();
				}};
		}
	}
	
	public static class TreeMVar extends MVar {
		private TreeMap<MVarKey, Object> data = null;
		
		public TreeMVar(String name) {
			super(name);
			this.data = new TreeMap<>();
		}
		
		public TreeMVar(String name, Object val) {
			super(name);
			this.data = new TreeMap<>();
			set(val);
		}
		
		protected TreeMVar(TreeMVar root, MVarKey path) {
			super(root, path);
			this.data = root.data;
		}
		
		@Override
		public MVar get(MVarKey key) {
			return new TreeMVar((TreeMVar) this.root, key);
		}
		
		@Override
		public MVarKey nextKey() {
			MVarKey tmp = this.path.append(null);
			MVarKey next = data.higherKey(tmp);
			if (next == null) return null;
			
			// if next node is at a higher level, return null (no more nodes on same level)
			if (this.path.size() > next.size()) return null;
			
			// if next node is at a lower level, splice it to same level
			return next.splice(this.path.size());
		}
		
		@Override
		public MVarKey prevKey() {
			return null;
		}

		@Override
		public boolean isDefined() {
			return data.containsKey(path);
		}
		
		@Override
		public boolean hasDescendents() {
			MVarKey next = data.higherKey(this.path);
			if (next == null) return false;
			return next.before(this.path.append(null));
		}
		
		@Override
		public Object doSetValue(MVarKey key, Object val) {
			data.put(key, val);
			return val;
		}
		
		@Override
		public Object unset() {
			return data.remove(this.path);
		}
		
		@Override
		public Object doGetValue(MVarKey key) {
			return data.get(key);
		}
		
		@Override
		protected Iterator<MVarKey> iterator() {
			return data.subMap(this.path, false, this.path.append(null), false).keySet().iterator();
		}
	}
	
	public static class CacheMVar extends MVar {
		private NodeReference ref;

		public CacheMVar(NodeReference ref) {
			super(ref.getName());
			this.ref = ref;
		}

		@Override
		public Object doGetValue(MVarKey key) {
			return ref.getObject(this.path.keys);
		}

		@Override
		public Object doSetValue(MVarKey key, Object val) {
			ref.set(val.toString(), key.keys);
			return null;
		}

		@Override
		public Object unset() {
			ref.kill(this.path.keys);
			return null;
		}

		@Override
		public boolean isDefined() {
			return ref.exists(this.path.keys);
		}

		@Override
		public boolean hasDescendents() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public MVar get(MVarKey key) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public MVarKey nextKey() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public MVarKey prevKey() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected Iterator<MVarKey> iterator() {
			// TODO Auto-generated method stub
			return null;
		}
		
		
	}
	
	public static class MVarKey implements Comparable<MVarKey>,Serializable {
		private Comparable[] keys;
		
		@SafeVarargs
		public MVarKey(Comparable... keys) {
			this.keys = keys;
		}
		
		public int size() {
			return keys.length;
		}
		
		public Comparable getLastKey() {
			return this.keys[this.keys.length-1];
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			toString(sb);
			return sb.toString();
		}
		
		public void toString(StringBuilder sb) {
			sb.append("(");
			for (Object key : keys) {
				if (key != keys[0]) sb.append(",");
				if (key instanceof String) {
					sb.append('"');
					sb.append(key);
					sb.append('"');
				} else {
					sb.append(key);
				}
			}
			sb.append(")");
		}
		
		@Override
		public int compareTo(MVarKey o) {
			if (o == this) return 0;
			
			int len = Math.min(keys.length, o.keys.length);
            for (int i = 0; i < len; i++) {
            	int comp = 0;
            	Comparable o1 = this.keys[i], o2 = o.keys[i];
            	
            	if (o1 == o2) {
            		comp = 0; // includes both null
            	} else if (o2 == null) {
            		comp = -1;
            	} else if (o1 == null) {
            		comp = 1;
            	} else if (o1 instanceof Number && o2 instanceof Number) {
            		comp = Double.compare(((Number) o1).doubleValue(), ((Number) o2).doubleValue());
            	} else if (o1 instanceof Number && o2 instanceof String) {
            		comp = -1;
            	} else if (o1 instanceof String && o2 instanceof Number) {
            		comp = 1;
            	} else if (!o1.getClass().equals(o2.getClass())) {
            		comp = o1.toString().compareTo(o2.toString());
            	} else {
            		comp = o1.compareTo(o2);
            	}
            	
            	if (comp != 0) {
            		return comp;
            	}
            }
            
            return Integer.compare(this.keys.length, o.keys.length);
		}
		
		public boolean after(MVarKey o) {
			return compareTo(o) > 0;
		}
		
		public boolean before(MVarKey o) {
			return compareTo(o) < 0;
		}
		
		public MVarKey append(Comparable... withkeys) {
			if (withkeys == null) withkeys = new Comparable[] {null};
			Comparable[] tmp = new Comparable[this.keys.length + withkeys.length];
			System.arraycopy(this.keys, 0, tmp, 0, this.keys.length);
			System.arraycopy(withkeys, 0, tmp, keys.length, withkeys.length);
			return new MVarKey(tmp);
		}
		
		public MVarKey splice(int length) {
			Comparable[] tmp = new Comparable[length];
			System.arraycopy(this.keys, 0, tmp, 0, length);
			return new MVarKey(tmp);
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof MVarKey) return this.compareTo((MVarKey) o) == 0;
			return false;
		}
		
		public static MVarKey valueOf(Comparable... keys) {
			return new MVarKey(keys);
		}
	}
	
}