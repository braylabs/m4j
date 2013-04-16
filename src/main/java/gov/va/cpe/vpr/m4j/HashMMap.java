package gov.va.cpe.vpr.m4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * First MMap implementation based on hash maps.
 * 
 * MMap = Multi-dimensional map (same as perl/mumps associative arrays)
 * MMap = MumpsMap?
 * 
 * Goals:
 * - multi-dimentional
 * - sparse
 * - null-save
 * - PERL style auto-vivification
 * 
 * @author brian
 */
public class HashMMap<K, V> extends MMap<K, V> {
	private Map<K, MMap<K, V>> data = new HashMap<K, MMap<K, V>>();
	
	@Override
	public Set<java.util.Map.Entry<K, MMap<K, V>>> entrySet() {
		return data.entrySet();
	}

	@Override
	protected MMap<K, V> resolveNode(K... keys) {
		MMap<K, V> e = this;
		for (K key : keys) {
			if (!data.containsKey(key)) {
				data.put(key,  new HashMMap<K,V>());
			}
			e = data.get(key);
		}
		return e;
	}
}