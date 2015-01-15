package gov.va.cpe.vpr.m4j.mmap;

import static org.junit.Assert.*;
import gov.va.cpe.vpr.m4j.mmap.MVar.MVarKey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;

import org.junit.Test;

public class MVarTest {

	@Test
	public void testMVarKeySort() {
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
				new MVarKey("ORC", 200, "BAR", "A"),
				new MVarKey("ORC", "A", "BAR"),
				new MVarKey("ORC", "a", "FOO"),
		};
		
		// array was composed in expected sort order, should be the same after sort
		MVarKey[] old = Arrays.copyOf(keys, keys.length);
		Arrays.sort(keys);
		assertEquals(keys,old);
		
		for (int i=0; i < keys.length; i++) {
			System.out.println(keys[i]);
			
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
		
	}
	
	@Test
	public void testMVarKeyAppend() {
		MVarKey k = MVarKey.valueOf("A",1,"Z");
		k = k.append("P",9,"Q");
		assertTrue(k.equals(MVarKey.valueOf("A",1,"Z","P",9,"Q")));
		
	}
	
	@Test
	public void test() {
		// check empty var
		MVar x = new MVar.TreeMVar("BEB");
		assertNull(x.val());
		assertFalse(x.exists());
		
		
		// set the value and a sub-value
		x.set("FOO");
		x.get("ORC",141.01,"A").set("BAR");
		assertEquals("FOO", x.val());
		assertTrue(x.exists());
		assertEquals("BAR", x.val("ORC",141.01,"A"));
		
		
		// setup some 3 sub-records
		for (int i=0; i < 3; i++) {
			x.get("ORC", 100, i).set(i);
			for (String key : Arrays.asList("A","B","C")) {
				x.get("ORC", 100, i, key).set(i + "-" + key);
			}
		}
		
		System.out.println(x);
		
		System.out.println("JUST The 100.1 rec");
		System.out.println(x.get("ORC",100,1));
	}
}
