package com.braylabs.m4j.global;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.h2.mvstore.MVStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.braylabs.m4j.global.MMap;
import com.braylabs.m4j.global.MMap.LocalMVar;
import com.braylabs.m4j.global.MMap.MVStoreMMap;
import com.braylabs.m4j.global.MMap.TreeMMap;
public class MMapTest {
	
	private MVStore mvstore;
	private File tmpfile;

	@Before
	public void before() {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (!tmpdir.endsWith(File.separator)) tmpdir += File.separator;
        tmpfile = new File(tmpdir, "MMapTest.data");
        tmpfile.deleteOnExit();
        if (tmpfile.exists()) tmpfile.delete();
     	mvstore = new MVStore.Builder().fileName(tmpfile.getAbsolutePath()).cacheSize(20).open();
	}
	
	@After
	public void after() {
		mvstore.close();
	}
	
	@Test
	public void testLocalMVar() {
		LocalMVar map = new LocalMVar("X");
		map.setValue("foo");
		map.getNode("X","Y","Z").setValue("bar");

		// foo value (should ignore default)
		assertEquals("foo", map.getValue());
		assertEquals("foo", map.getValue(new String[] {}));
		
		// 3 ways to get the "bar" value
		assertEquals("bar", map.getValue("X","Y","Z"));
		assertEquals("bar", map.getNode("X","Y","Z").getValue());
		assertEquals("bar", map.getNode("X").getNode("Y").getNode("Z").getValue());
		
		// missing value
		assertNull(map.getNode("X","Y").getValue());
		
		// shallow size is for each level
		assertEquals(1, map.size());
		assertEquals(1, map.getNode("X").size());
		assertEquals(1, map.getNode("X","Y").size());
		assertEquals(0, map.getNode("X","Y","Z").size());
		
		System.out.println(map);
	}
	
	@Test
	public void testMVStore() {
		MVStoreMMap glob = new MVStoreMMap(mvstore, "foo");
		
		// assert its empty
		assertEquals("foo", glob.getName());
		assertEquals(0, glob.size());
		assertFalse(glob.containsKey("bar"));
		assertNull(glob.getValue());
		assertFalse(glob.getNode("bar","baz").exists());
		assertNull(glob.getValue("bar","baz"));
		assertTrue(glob.keySet().isEmpty());
		
		// set a couple values
		glob.getNode("bar","baz").setValue("hello world");
		assertEquals("hello world", glob.getValue("bar","baz"));
		
		// commit/close/reopen global
		mvstore.commit();
		
		// value should still be there
		glob = new MVStoreMMap(mvstore, "foo");
		assertEquals(1, glob.size());
		assertEquals(1, glob.getNode("bar").size());
		assertEquals("hello world", glob.getValue("bar","baz"));
		assertFalse(glob.exists());
		assertFalse(glob.getNode("bar").exists());
		assertTrue(glob.getNode("bar").getNode("baz").exists());
		assertEquals("hello world", glob.getNode("bar").getNode("baz").getValue());
		
		System.out.println(glob);
	}
	
	
	@Test
	public void testPatExample() {
		MVStoreMMap map = new MVStoreMMap(mvstore, "pats");
//		MMap map = new TreeMMap("pats");
		
		MMap<String,String> pat = map.getNode("229");
		pat.getNode("name","first").setValue("Brian");
		pat.getNode("name","last").setValue("Bray");
		
		System.out.println(map);
	}

}
