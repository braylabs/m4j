package gov.va.cpe.vpr.m4j.lang;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.*;
import static org.junit.Assert.*;
import gov.va.cpe.vpr.m4j.global.MVar;
import gov.va.cpe.vpr.m4j.global.MVar.TreeMVar;

import org.junit.Test;

public class MUMPSTests {
	String str = "foo.bar.baz";
	String d = ".";
	
	@Test
	public void test$I() {
		MVar var = new TreeMVar("FOO");
		
		// Undefined is defined as 0
		assertFalse(var.isDefined());
		assertEquals(0, $I(var, 0));
		assertTrue(var.isDefined());
		assertEquals(0, var.val());
		
		// no increment param is treated as 1
		assertEquals(1, $I(var));
		
		// decrement back to 0
		assertEquals(0, $I(var,-1));
		
		// if either is a decimal, return a decimal
		assertEquals(1.1, $I(var, 1.1));
		
		// string values should work too!
		var.set("1");
		assertEquals(2.1, $I(var, "1.1"));
		
		// and exponential values
		var.set("1E3");
		assertEquals(2000, $I(var, "1E3"));
		
		// null string is zero
		var.set("");
		assertEquals(0, $I(var,""));
		
		// non-canatonical numbers are ok too!
		var.set("0123.2100");
		assertEquals(123.21, $I(var,"+000000"));
		
		
	}
	
	
	@Test
	public void test$O() {
		MVar var = new TreeMVar("A");
		var.get("A").set(1);
		var.get("B").set(2);
		var.get("C").set(3);
		
		// root node (unscripted) is always null (TODO: double check with cache)
		assertNull($O(var));
		
		// simple cases
		assertEquals("B", $O(var.get("A")));
		assertNull($O(var.get("C")));
		
		// with targer
		$O(var.get("A"), 1, var.get("A","RESULTS"));
		assertEquals("B", var.get("A", "RESULTS").val());
		
		// TODO: Test deep level does not jump up to shallow level
		var = new TreeMVar("BEB");
		var.get("FEE","FI","FO","FUM");
		
		// TODO: Test shallow level does not dive to deeper level
		
		// TODO: Test intermediate subscripts that don't exist stay at the same level
		
		// TODO: Test reverse direction
		
		// TODO: Test target parameter
		
		// TODO: Test against cache
		
	}
	
	@Test
	public void test$D() {
		MVar var = new TreeMVar("FOO");
		
		// check all the available values
		assertEquals(0, $D(var));
		var.set("FOO");
		assertEquals(1, $D(var));
		var.unset();
		assertEquals(0, $D(var));
		var.get(1,2,3).set("FOO");
		assertEquals(10, $D(var));
		var.set("FOO");
		assertEquals(11, $D(var));
		
		// check writing return to a target variable
		$D(var, var.get("RESULTS"));
		assertEquals(11, var.val("RESULTS"));
	}
	
	@Test
	public void test$G() {
		MVar var = new MVar.TreeMVar("FOO");
		var.get(1,2,3).set("BAR");
		
		// undefined returns empty string
		assertFalse(var.isDefined());
		assertEquals("", $G(var));
		
		// defined variable returns its value
		assertEquals("BAR", $G(var.get(1,2,3)));
		
		// undefined variable with a default value
		assertEquals("ZZZ", $G(var, "ZZZ"));
		
		// undefined variable with a default variable
		assertEquals("BAR", $G(var, var.get(1,2,3)));
		
		// undefined variable with undefined default variable == exception
		try {
			$G(var, var);
			fail("Excepected exception");
		} catch (UndefinedVariableException ex) {
			// expected
		}
	}
	
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
