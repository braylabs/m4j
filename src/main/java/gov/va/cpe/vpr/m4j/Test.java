package gov.va.cpe.vpr.m4j;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

public class Test {
	
	public static void main(String[] args) {
		// test if I can efficiently store lots of fields in a single map from MVStore
        String tmpfile = System.getProperty("java.io.tmpdir") + "/CacheMgr.data";
     	MVStore mvstore = new MVStore.Builder().fileName(tmpfile).cacheSize(20).open();
     	
     	MVMap mgr = mvstore.openMap("bigdata");
		
		System.out.println(mgr.getSize());
		
		long start = System.currentTimeMillis();
		System.out.println("Writing....");
		for (int i=0; i < 100000; i++) {
			mgr.put("bar" + i, "foobar"+i);
			if (i%10000==0) {System.out.println(i + " "); mvstore.store();}
		}
		mvstore.store();
		mgr.close();
		System.out.println("Done in " + (System.currentTimeMillis()-start) + "ms");
		System.exit(1);
	}
	
	
	
	
	private static class Global {
		
	}
}
