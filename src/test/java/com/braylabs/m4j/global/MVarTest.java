package com.braylabs.m4j.global;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.global.MMap.MVStoreMMap;
import com.braylabs.m4j.global.MVar.MVarKey;

public class MVarTest {
	
	private MVStore mvstore;
	private File tmpfile;

	@Before
	public void before() {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (!tmpdir.endsWith(File.separator)) tmpdir += File.separator;
        tmpfile = new File(tmpdir, "MVarTest.data");
        tmpfile.deleteOnExit();
        if (tmpfile.exists()) tmpfile.delete();
     	mvstore = new MVStore.Builder().fileName(tmpfile.getAbsolutePath()).cacheSize(20).open();
	}
	
	@After
	public void after() {
		mvstore.close();
	}

	MVarKey[] keys = new MVarKey[] {
			new MVarKey("ORC"),
			new MVarKey("ORC", 141.01),
			new MVarKey("ORC", 141.01, '\0'), // same as Character.MIN_VALUE?
			new MVarKey("ORC", 141.01, -100),
			new MVarKey("ORC", 141.01, 0),
			new MVarKey("ORC", 141.01, ""),
			new MVarKey("ORC", 141.01, "0"),
			new MVarKey("ORC", 141.01, 'F', 0),
			new MVarKey("ORC", 141.01, "FOO", 0),
			new MVarKey("ORC", 141.01, "FOO", "A"),
			new MVarKey("ORC", 141.01, "FOOD"),
			new MVarKey("ORC", 141.01, null),
			new MVarKey("ORC", 200, "BAR"),
			new MVarKey("ORC", 200, "BAR", "A", "B", "C", "D"),
			new MVarKey("ORC", "A", "BAR"),
			new MVarKey("ORC", "a", "FOO"),
	};
	
	@Test
	public void testMVarKeySort() {

		// array was composed in expected sort order, should be the same after sort
		MVarKey[] old = Arrays.copyOf(keys, keys.length);
		Arrays.sort(keys);
		assertEquals(keys,old);
		
		for (int i=0; i < keys.length; i++) {
//			System.out.println(keys[i]);
			
			// equals
			assertTrue(keys[i].compareTo(keys[i]) == 0);
			assertTrue(keys[i].equals(keys[i]));
			
			// greater than previous
			if (i > 0) assertTrue(keys[i].compareTo(keys[i-1]) > 0);
			
			// less than next
			if ((i+1) < keys.length) assertTrue(keys[i].compareTo(keys[i+1]) < 0);
		}

		// assert equals (but numeric is not the same as string number)
		assertTrue(MVarKey.valueOf("A").equals(MVarKey.valueOf("A")));
		assertFalse(MVarKey.valueOf("2").equals(MVarKey.valueOf(2)));
		
		// assert case sensitive (upper before lower)
		assertTrue(MVarKey.valueOf("a").after(MVarKey.valueOf("A")));
		assertTrue(MVarKey.valueOf("A").before(MVarKey.valueOf("a")));
		
		// assert numbers sort before letters
		assertTrue(MVarKey.valueOf(1).before(MVarKey.valueOf("A")));
		assertTrue(MVarKey.valueOf("A").after(MVarKey.valueOf(9)));
		assertTrue(MVarKey.valueOf("A", 1).before(MVarKey.valueOf("A", "A")));
		
		// assert shorter length strings before longer strings
		assertTrue(MVarKey.valueOf("FOO").before(MVarKey.valueOf("FOOD")));
		
		// assert less number of keys before greater number of keys
		assertTrue(MVarKey.valueOf("A","B","C").before(MVarKey.valueOf("A","B","C","D")));
		
		// assert weird cases: empty string before a null character
		assertTrue(MVarKey.valueOf("").before(MVarKey.valueOf('\0')));
		
		// problem case: null on the end was not .equal()?
		assertEquals(MVarKey.valueOf("A",1,null), MVarKey.valueOf("A",1,null));
	}
	
	@Test
	public void testMVarKeyAppend() {
		MVarKey k = MVarKey.valueOf("A",1,"Z");
		k = k.append("P",9,"Q");
		assertTrue(k.equals(MVarKey.valueOf("A",1,"Z","P",9,"Q")));
		
	}
	
	@Test
	public void testLocal() {
		validate(new MVar.TreeMVar("BEB"));
	}
	
	@Test
	public void testGlobal() {
		validate(new MVar.MVStoreMVar(mvstore, "BEB"));
	}
	
	public void validate(MVar x) {
		assertNull(x.val());
		assertFalse(x.hasDescendents());
		
		// load all the keys
		for (MVarKey key : keys) {
			x.doSetValue(key, "X");
		}
		
		// isDefined()
		assertTrue(x.get("ORC","A","BAR").isDefined());
		assertFalse(x.isDefined());
		
		// hasDescendents()
		assertTrue(x.hasDescendents());
		assertFalse(x.get("ORC","A","BAR").hasDescendents());
		assertTrue(x.get("ORC").hasDescendents());
		
		// names
		assertEquals("BEB", x.getName());
		assertEquals("BEB", x.get("ORC",141.01).getName());
		assertEquals("BEB(\"ORC\",141.01)", x.get("ORC",141.01).getFullName());
		
		// next
		assertNull(x.nextKey()); // inital key
		assertEquals(MVarKey.valueOf("ORC", 200), x.get("ORC",141.01).nextKey());
		assertEquals(MVarKey.valueOf("ORC", 200,"BAR", "A", "B"), x.get("ORC", 200, "BAR", "A", "A").nextKey()); // does not exist
		assertEquals(null, x.get("ORC", 200, "BAR", "A").nextKey()); // no more in same level
//		x.next()
		System.out.println(x);
	}
}
