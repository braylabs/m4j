package gov.va.cpe.vpr.m4j;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVMap.MapBuilder;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.Page;

public class MVStoreMMap extends MMap<String, String> {
	private String name;
	private MVMap<Integer, Map<String,Object>> mvmap;

	public MVStoreMMap(MVStore store, String name) {
		this.name = name;
		this.mvmap = store.openMap(this.name);
	}
	
	@Override
	protected MMap<String, String> resolveNode(String... keys) {
		List<String> keyList = Arrays.asList(keys);
		keyList.remove(keys.length);
		int hash = keyList.hashCode();
		
		if (mvmap.containsKey(keys)) {
		}
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<java.util.Map.Entry<String, MMap<String, String>>> entrySet() {
		return new EntrySetWrapper();
	}
	
	private class EntrySetWrapper extends AbstractSet<Entry<String, MMap<String, String>>> {

		@Override
		public int size() {
			return MVStoreMMap.this.mvmap.size();
		}

		@Override
		public Iterator<java.util.Map.Entry<String, MMap<String, String>>> iterator() {
			return null;
		}
		
	}
}
