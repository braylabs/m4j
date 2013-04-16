package gov.va.cpe.vpr.m4j.mparser;

import gov.va.cpe.vpr.m4j.HashMMap;
import gov.va.cpe.vpr.m4j.MMap;

import java.util.HashMap;
import java.util.Map;

public class MContext {
	
	private Map<String, MMap> globals = new HashMap<String, MMap>();
	
	public MMap getGlobal(String name) {
		if (!globals.containsKey(name)) {
			globals.put(name, new HashMMap());
		}
		return globals.get(name);
	}

}
