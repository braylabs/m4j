package gov.va.cpe.vpr.m4j.mmap;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.fasterxml.jackson.core.JsonGenerator;

public interface MMap<K extends Serializable & Comparable<K>, V extends Serializable> 
	extends Map<K,V>, Map.Entry<K, V> { 
	
	public String getName();
	public K[] getPath();
	public boolean exists();
	public V getValue(K... keys);
	public MMap<K,V> getNode(K... keys);
	public void toString(StringBuilder sb);
	
	public abstract class AbstractMMap<K extends Serializable & Comparable<K>, V extends Serializable> 
		extends AbstractMap<K, V> implements MMap<K, V> {
		
		private String name;
		private K[] key;
		private V val;
		protected Map<K[],V> data;
		private boolean valueCached;
		
		protected AbstractMMap(Map<K[],V> data, String name, K... key) {
			this.name = name;
			this.key = key;
			this.data = data;
		}
		
		@Override
		public String getName() {
			return this.name;
		}
		
		@Override
		public K[] getPath() {
			return this.key;
		}

		@Override
		public K getKey() {
			return this.key[this.key.length-1];
		}
		
		@Override
		public boolean exists() {
			return data.containsKey(getPath());
		}

		@Override
		public V setValue(V value) {
			this.data.put(getPath(), value);
			this.valueCached=true;
			return this.val=value;
		}
		
		@Override
		public V getValue() {
			if (this.valueCached) {
				return this.val;
			}
			this.valueCached = true;
			return this.val = this.data.get(getPath());
		}
		
		@Override
		public V getValue(K... keys) {
			return getNode(keys).getValue();
		}
		
		@Override
		public MMap<K, V> getNode(K... keys) {
			int pathLen = getPath().length;
			K[] subkey = Arrays.copyOf(getPath(), pathLen + keys.length);
			System.arraycopy(keys, 0, subkey, pathLen, keys.length);
			return doGetNode(subkey);
		}
		
		protected abstract MMap<K,V> doGetNode(K... keys);
		
		// serialization stuff ----------------------------------------------------
		public String toJSON() {
			JsonGenerator gen = null;
			writeJSON(gen);
			return null;
		}
		
		private void writeJSON(JsonGenerator gen) {
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			toString(sb);
			return sb.toString();
		}
		
		public void toString(StringBuilder sb) {
			V val = getValue();
			if (val != null) {
				sb.append(getName());
				if (getPath().length > 0) {
					sb.append("(");
					for (K k: getPath()) {
						if (sb.charAt(sb.length()-1) != '(') {
							sb.append(",");
						}
						sb.append(k);
					}
					sb.append(")=");
				} else {
					sb.append("=");
				}
				sb.append(val);
				sb.append("\n");
			}
			for (K k : keySet()) {
				getNode(k).toString(sb);
			}
		}
	}
	
	public class TreeMMap<K extends Serializable & Comparable<K>, V extends Serializable> 
		extends AbstractMMap<K, V> {
		private NavigableMap<K[], V> treedata;
		
		public TreeMMap(String name, K... key) {
			super(new TreeMap<K[], V>(new Comparator<K[]>() {
				@Override
				public int compare(K[] o1, K[] o2) {
					if (o1 == o2) return 0;
					if (o1 == null) return -1;
					if (o2 == null) return 1;
		            int o1Len = o1.length;
		            int o2Len = o2.length;
		            
					int len = Math.min(o1Len, o2Len);
	                for (int i = 0; i < len; i++) {
	                	if (o1[i] == null) return -1;
	                	if (o2[i] == null) return 1;
	                	int comp = o1[i].compareTo(o2[i]);
	                    if (comp != 0) {
	                        return comp;
	                    }
	                }
	                
	                return o1Len == o2Len ? 0 : o1Len < o2Len ? -1 : 1;
				}
			}), name, key);
			this.treedata = (NavigableMap<K[], V>) this.data;
		}
		
		protected TreeMMap(TreeMMap<K,V> parent, String name, K... key) {
			super(parent.treedata, name, key);
			this.treedata = parent.treedata;
		}

		@Override
		protected MMap<K,V> doGetNode(K... keys) {
			return new TreeMMap<K, V>(this, getName(), keys);
		}

		@Override
		public Set<java.util.Map.Entry<K, V>> entrySet() {
			Set<Entry<K,V>> ret = new HashSet<Entry<K,V>>();
			
			// find the next subkey
			int keysize = getPath().length + 1;
			K[] startkey = Arrays.copyOf(getPath(), keysize);
			startkey = treedata.higherKey(startkey);
			if (startkey != null) {
				Iterator<K[]> curs = treedata.tailMap(startkey).keySet().iterator();
				K[] key = null;
				while (curs.hasNext()) {
					key = curs.next();
					if (key.length != keysize) break;
					ret.add(getNode(key));
				}
				// no direct children, make a phantom node
				if (ret.isEmpty() && key != null) { 
					ret.add(new TreeMMap<K,V>(this, getName(), Arrays.copyOf(key, keysize)));
				}
			}
			return ret;
		}
	}
	
	public class LocalMVar extends TreeMMap<String, String> {
		public LocalMVar(String name, String... key) {
			super(name, key);
		}
	}
	
	public class MVStoreMMap extends AbstractMMap<String, String> {
		private MVMap<String[], String> mvmap;
		
		public MVStoreMMap(MVStore store, String name, String... keys) {
			this(store.openMap(name), name, keys);
		}
		
		public MVStoreMMap(MVMap map, String name, String... keys) {
			super(map, name, keys);
			this.mvmap = map;
		}
		
		public MVMap<String[], String> getMVMap() {
			return this.mvmap;
		}
		
		@Override
		protected MMap<String,String> doGetNode(String... keys) {
			return new MVStoreMMap(this.mvmap, getName(), keys);
		}
		
		@Override
		public Set<java.util.Map.Entry<String, String>> entrySet() {
			Set<Entry<String,String>> ret = new HashSet<Entry<String, String>>();
			int keysize = getPath().length + 1;
			String[] startkey = Arrays.copyOf(getPath(), keysize);
			startkey = mvmap.higherKey(startkey);
			if (startkey != null) {
				Cursor<String[]> curs = mvmap.keyIterator(startkey);
				String[] key = null;
				while (curs.hasNext()) {
					key = curs.next();
					if (key.length != keysize) break;
					ret.add(new MVStoreMMap(this.mvmap, getName(), key));
				}
				// no direct children, make a phantom node
				if (ret.isEmpty() && key != null) { 
					ret.add(new MVStoreMMap(this.mvmap, getName(), Arrays.copyOf(key, keysize)));
				}				
			}
			return ret;
		}
	}
}
