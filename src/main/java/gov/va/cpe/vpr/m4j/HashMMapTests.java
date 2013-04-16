package gov.va.cpe.vpr.m4j;

import static org.junit.Assert.*;

import org.junit.Test;

public class HashMMapTests {
	private MMap<String, Object> map = buildMap();
	
	@Test
	public void test1() {
		testMap(map);
	}
	
	public void testMap(MMap<String, Object> map) {
		// foo value (should ignore default)
		assertEquals("foo", map.get());
		assertEquals("foo", map.getValue());
		assertEquals("foo", map.getValue(null));
		assertEquals("foo", map.getValue(new String[] {}));
		
		// 3 ways to get the "bar" value
		assertEquals("bar", map.getValue("X","Y","Z"));
		assertEquals("bar", map.get("X","Y","Z").getValue());
		assertEquals("bar", map.get("X").get("Y").get("Z").getValue());
		
		// missing value
		assertNull(map.get("X","Y").getValue());
		
		// shallow size is for each level
		assertEquals(1, map.size());
		assertEquals(1, map.get("X").size());
		assertEquals(1, map.get("X","Y").size());
		assertEquals(0, map.get("X","Y","Z").size());
	}
	
	public MMap<String, Object> buildMap() {
		HashMMap<String, Object> map = new HashMMap<String, Object>();
		map.setValue("foo");
		map.get("X","Y","Z").setValue("bar");
		return map;
	}
}
