package gov.va.cpe.vpr.m4j;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;


public abstract class MMap<K, V> extends AbstractMap<K, MMap<K,V>> {
	private V value;
	
	protected abstract MMap<K, V> resolveNode(K... keys);
	
	@JsonIgnore
	public MMap<K,V> get(K... keys) {
		if (keys == null) return this;
		return resolveNode(keys);
	}
	
	@Override
	public MMap<K, V> get(Object key) {
		if (key == null) return this;
		return resolveNode((K)key);
	}
	
	@JsonIgnore
	public MMap<K,V> setValue(V val) {
		this.value = val;
		return this;
	}
	
	public V get() {
		return getValue();
	}
	
	public V getValue() {
		return this.value;
	}
	
	public V getValue(K... keys) {
		return get(keys).value;
	}

	// serialization stuff ----------------------------------------------------
	public String toJSON() {
		JsonGenerator gen = null;
		writeJSON(gen);
		return null;
	}
	
	private void writeJSON(JsonGenerator gen) {
	}
	
    public String toString() {
		Iterator<Entry<K, MMap<K, V>>> i = entrySet().iterator();
		if (!i.hasNext())
			return "{" + getValue() + "}";

		StringBuilder sb = new StringBuilder();
		sb.append('{');
		for (;;) {
			java.util.Map.Entry<K, MMap<K, V>> e = i.next();
			K key = e.getKey();
			MMap<K, V> value = e.getValue();
			sb.append(key == this ? "(this Map)" : key);
			sb.append('=');
			sb.append(value == this ? "(this Map)" : value);
			if (!i.hasNext())
				return sb.append('}').toString();
			sb.append(", ");
		}
    }
}
