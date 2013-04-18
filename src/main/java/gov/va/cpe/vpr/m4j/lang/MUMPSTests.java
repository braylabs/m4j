package gov.va.cpe.vpr.m4j.lang;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class MUMPSTests {
	String str = "foo.bar.baz";
	String d = ".";
	
	@Test
	public void test$A() {
		String s = "foo";
		
		assertEquals(102, $A(s));
		assertEquals(102, $A(s, 1));
		assertEquals(111, $A(s, 2));
		assertEquals(111, $A(s, 3));
		
		// -1 return scenarios
		assertEquals(-1, $A(s, -1));
		assertEquals(-1, $A(s, 0));
		assertEquals(-1, $A(s, 10));
		assertEquals(-1, $A(""));
		assertEquals(-1, $A(null));
	}
	
	@Test
	public void test$C() {
		assertEquals("f", $C(102));
		assertEquals("o", $C(111));
		assertEquals("foo", $C(102,111,111));
		
		// exception cases
		try {
			assertEquals("foo", $C());
			fail("exception expected");
		} catch (IllegalArgumentException ex) {
			// expected
		}
	}
	
	@Test
	public void test$E() {
		
		// single character returns
		assertEquals("f", $E(str));
		assertEquals("f", $E(str, 1));
		assertEquals("o", $E(str, 2));
		assertEquals("o", $E(str, 3));
		
		// multi-character returns
		assertEquals("foo", $E(str, 1, 3));
		assertEquals("foo.bar.baz", $E(str, 1, 300));
		
		
		// empty return senarios
		assertEquals("", $E(str, -1));
		assertEquals("", $E(str, -10));
		assertEquals("", $E(str, 5, 3));
		assertEquals("", $E(null));
	}
	
	@Test
	public void test$F() {
		assertEquals(2, $F(str, "f"));
		assertEquals(6, $F(str, "b"));
		assertEquals(10, $F(str, "b", 6));
		assertEquals(4, $F(str, "foo"));
		assertEquals(8, $F(str, "bar"));
		assertEquals(str.length()+1, $F(str, "baz"));
		
		// non maching scenarios
		assertEquals(0, $F(str, "F")); // case-sensitive
		assertEquals(0, $F(str, "f", 1000));
		
		// 1 return scenarios
		assertEquals(1, $F(str, ""));
		assertEquals(1, $F("", ""));
		
		// 0 return scenarios
		assertEquals(0, $F("", "a"));
		assertEquals(0, $F(null, "a"));
		assertEquals(0, $F(str, "q"));
		assertEquals(0, $F(str, null));
		assertEquals(0, $F(str, "zoo"));
	}
	
	@Test
	public void test$P() {
		// single piece selection
		assertEquals("foo", $P(str, d));
		assertEquals("foo", $P(str, d, 1));
		assertEquals("bar", $P(str, d, 2));
		assertEquals("baz", $P(str, d, 3));
		assertEquals("foo", $P(str, d, 1, 1));
		assertEquals("bar", $P(str, d, 2, 2));
		assertEquals("baz", $P(str, d, 3, 3));
		
		// multi-piece selection
		assertEquals("foo.bar", $P(str, d, 1, 2));
		assertEquals("bar.baz", $P(str, d, 2, 3));
		assertEquals("baz", $P(str, d, 3, 4));
		assertEquals("foo.bar.baz", $P(str, d, 1, 3));
		assertEquals("foo.bar.baz", $P(str, d, 1, 10));
		
		// empty return scenarios
		assertEquals("", $P(str, "", 0)); // empty delimiter
		assertEquals("", $P(str, d, 0)); // 1-based index
		assertEquals("", $P(str, d, -1)); // < 1
		assertEquals("", $P(str, d, 100)); // > token count
		assertEquals("", $P(str, d, 2, 1)); // last < first
		
		// other cases
		assertEquals(str, $P(str, "^", 1)); // ^ is not a valid delmiter, return full string
	}
	
	@Test
	public void test$R() {
		for (int i=0; i <= 100; i++) {
			assertEquals(50, $R(100), 50);
		}
	}
	
	@Test
	public void test$RE() {
		assertEquals("cba", $RE("abc"));
		assertEquals(321, $RE(123));
		
		assertEquals("", $RE(""));
		assertEquals("", $RE(null));
	}
}