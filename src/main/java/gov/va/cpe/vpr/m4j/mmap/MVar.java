package gov.va.cpe.vpr.m4j.mmap;

import java.util.Set;
import java.util.TreeMap;

/**
 * TODO:
 * - re-add the MVStore peristent implementation
 * - add a toMap() method/mechanism
 * - add a size() mechanism
 * - add an interable mechanism
 * - do the $O,$D,$G implementations, see what else is needed
 * - in toString(), make it indent values like %G does
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
	
	public Object val() {
		return doGetValue(this.path);
	}
	
	public Object val(Comparable<?>... keys) {
		return get(keys).val();
	}
	
	public MVar get(Comparable<?>... keys) {
		return get(this.path.append(keys));
	}
	
	public Object set(Object val) {
		return doSetValue(this.path, val);
	}
	
	// abstract methods -------------------------------------------------------

	/* Returns this nodes value, same as get(null) */
	public abstract Object doGetValue(MVarKey key);
	public abstract Object doSetValue(MVarKey key, Object val);
	public abstract boolean exists();
	public abstract MVar get(MVarKey key);
	protected abstract Set<MVarKey> keySet(); 

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (exists()) {
			sb.append(getName());
			if (this.path.size() > 0) path.toString(sb);	
			sb.append("=");
			sb.append(val());
		}
		
		for (MVarKey key : keySet()) {
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
	
	public static class TreeMVar extends MVar {
		private TreeMap<MVarKey, Object> data = null;
		
		public TreeMVar(String name) {
			super(name);
			this.data = new TreeMap<>();
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
		public boolean exists() {
			return data.containsKey(path);
		}
		
		@Override
		public Object doSetValue(MVarKey key, Object val) {
			return data.put(key, val);
		}
		
		@Override
		public Object doGetValue(MVarKey key) {
			return data.get(key);
		}
		
		@Override
		protected Set<MVarKey> keySet() {
			return data.subMap(this.path, false, this.path.append(null), false).keySet();
		}
		
		

		
	}
	
	public static class MVarKey implements Comparable<MVarKey> {
		private Comparable[] keys;
		
		@SafeVarargs
		public MVarKey(Comparable... keys) {
			this.keys = keys;
		}
		
		public int size() {
			return keys.length;
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
            	
            	if (o2 == null) {
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