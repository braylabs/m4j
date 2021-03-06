package com.braylabs.m4j.lang;

import org.junit.Test;

import com.braylabs.m4j.lang.MVal.BinaryOp;
import com.braylabs.m4j.lang.MVal.MatchOp;
import com.braylabs.m4j.lang.MVal.UnaryOp;

import static com.braylabs.m4j.lang.MVal.valueOf;
import static org.junit.Assert.*;

public class MValTests {

	
	@Test
	public void testADD() {
		// string values are 0, so adding strings is 0
		assertEquals(0, valueOf("A").toNumber());
		assertEquals(0, testNum("A","B", BinaryOp.ADD));
		
		// if the strings starts with numbers, they are used and the rest is ignored
		assertEquals(34, testNum("22 jump street", "12 monkeys", BinaryOp.ADD));
		
		// Concatenate 2 numbers		
		assertEquals(100100, testNum(100, 100, BinaryOp.CONCAT));
		
		// 1000 + 0
		assertEquals(1000, testNum("1E3", "A", BinaryOp.ADD));
	}
	
	@Test
	public void testEXP() {
		assertEquals(1000, testNum("10", "3", BinaryOp.EXP));
		assertEquals(0, testNum(0, 0, BinaryOp.EXP));
		assertEquals(0, testNum(0, 25, BinaryOp.EXP));
		assertEquals(1, testNum(100, 0, BinaryOp.EXP));
		
		//1**n: 1 raised to the power of any number (positive, negative, or zero) is 1
		assertEquals(1, testNum(1, 10, BinaryOp.EXP));
		assertEquals(1, testNum(1, -10, BinaryOp.EXP));
		assertEquals(1, testNum(1, 0, BinaryOp.EXP));

	}
	
	@Test
	public void testMOD() {
		assertEquals(.5, testNum(1.5, 1, BinaryOp.MOD));
	}
	
	@Test
	public void testDIV() {
		// evaluate this many places
		assertEquals(.3333333333333333333, testNum(1, 3, BinaryOp.DIV));
	}
	
	@Test
	public void testINTDIV() {
		// basically just drop the remainder off; always rounds down
		assertEquals(0, testNum(5,10, BinaryOp.INT_DIV));
		assertEquals(1, testNum(15,8, BinaryOp.INT_DIV));
	}
	
	@Test
	public void testGT() {
		assertEquals(1, testNum(5,1, BinaryOp.GT));
		assertEquals(0, testNum(0,1, BinaryOp.GT));
		assertEquals(0, testNum(1,1, BinaryOp.GT));

	}
	
	@Test
	public void testLT() {
		assertEquals(0, testNum(5,1, BinaryOp.LT));
		assertEquals(1, testNum(0,1, BinaryOp.LT));
		assertEquals(0, testNum(1,0, BinaryOp.LT));
	}
	
	@Test
	public void testEQ_NEQ() {
		// test EQ both ways
		assertEquals(0, testNum("007","7", BinaryOp.EQ));
		assertEquals(1, testNum(007,"7", BinaryOp.EQ));
		assertEquals(1, testNum(007,7, BinaryOp.EQ));
		assertEquals(0, testNum(007.01,7, BinaryOp.EQ));
		assertEquals("0", testStr("007","7", BinaryOp.EQ));
		assertEquals("1", testStr(007,"7", BinaryOp.EQ));
		assertEquals("1", testStr(007,7, BinaryOp.EQ));
		assertEquals("0", testStr(007.01,7, BinaryOp.EQ));
		
		// test NEQ both ways
		assertEquals(0, testNum("007","7", BinaryOp.NEQ));
		assertEquals(0, testNum(007,"7", BinaryOp.NEQ));
		assertEquals(0, testNum(007,7, BinaryOp.NEQ));
		assertEquals(1, testNum(007.01,7, BinaryOp.NEQ));
		assertEquals("0", testStr("007","7", BinaryOp.NEQ));
		assertEquals("0", testStr(007,"7", BinaryOp.NEQ));
		assertEquals("0", testStr(007,7, BinaryOp.NEQ));
		assertEquals("1", testStr(007.01,7, BinaryOp.NEQ));
		
		// strange cases
		assertEquals(0, testNum("007", 7, BinaryOp.EQ));
		assertEquals(0, testNum("007", "7", BinaryOp.EQ));
		assertEquals(1, testNum(007, "7", BinaryOp.EQ));
//		assertEquals(1, testNum(+"007", "7", BinaryOp.EQ));
	}
	
	@Test
	public void testRandom() {
		// generate 100k random numbers and test them
		for (int i=0; i < 100_000; i++) {
			double n1 = Math.random(), n2 = Math.random();
			
			if (n1 > n2) assertEquals(1, testNum(n1,n2,BinaryOp.GT));
			if (n1 >= n2) assertEquals(1, testNum(n1,n2,BinaryOp.GTE));
			if (n1 < n2) assertEquals(1, testNum(n1,n2,BinaryOp.LT));
			if (n1 <= n2) assertEquals(1, testNum(n1,n2,BinaryOp.LTE));
			if (n1 == n2) assertEquals(1, testNum(n1,n2,BinaryOp.EQ));
			if (n1 != n2) assertEquals(1, testNum(n1,n2,BinaryOp.NEQ));
			
		}
	}
	
	@Test
	public void testPrecidence() {
		assertEquals(9, testNum(testNum(1,2, BinaryOp.ADD),3,BinaryOp.MULT));
/*		
		WRITE "1 + 2 * 3 = ", 1 + 2 * 3,!  // returns 9
		 WRITE "2 * 3 + 1 = ", 2 * 3 + 1,!  // returns 7
		 WRITE "1 + (2 * 3) = ", 1 + (2 * 3),!  // returns 7
		 WRITE "2 * (3 + 1) = ", 2 * (3 + 1),!  // returns 8
*/
		
	}
	
	@Test
	public void testConcat() {
		assertEquals(100100, testNum(100,100, BinaryOp.CONCAT));
		assertEquals("AB", testStr("A","B", BinaryOp.CONCAT));
		assertEquals("12", testStr("1","2", BinaryOp.CONCAT));
	}
	
	@Test
	public void testContains() {
		assertEquals(1, testNum("FOOD", "FOO", BinaryOp.CONTAINS));
		assertEquals("1", testStr("FOOD", "FOO", BinaryOp.CONTAINS));
		assertEquals(0, testNum("FOO", "FOOD", BinaryOp.CONTAINS));
		assertEquals("0", testStr("FOO", "FOOD", BinaryOp.CONTAINS));

		assertEquals(0, testNum("FOOD", "FOO", BinaryOp.NOT_CONTAINS));
		assertEquals("0", testStr("FOOD", "FOO", BinaryOp.NOT_CONTAINS));
		assertEquals(1, testNum("FOO", "FOOD", BinaryOp.NOT_CONTAINS));
		assertEquals("1", testStr("FOO", "FOOD", BinaryOp.NOT_CONTAINS));
	}
	
	@Test
	public void testPOS_NEG() {
		// same as: +"007"="7"
		assertEquals(1, valueOf("007").apply(UnaryOp.POS).apply(BinaryOp.EQ, valueOf("7")).toNumber());
		assertEquals("1", valueOf("007").apply(UnaryOp.POS).apply(BinaryOp.EQ, valueOf("7")).toString());
	}
	
	@Test
	public void testNOT() {
		// W '"A" // returns 1
		assertEquals(1, valueOf("A").apply(UnaryOp.NOT).toNumber());
		
		// W '1 // returns 0
		assertEquals(0, valueOf(1).apply(UnaryOp.NOT).toNumber());
		
		// w '0 // returns 1
		assertEquals(1, valueOf(0).apply(UnaryOp.NOT).toNumber());
		
		// w '-1 // returns 0
		assertEquals(0, valueOf(-1).apply(UnaryOp.NOT).toNumber());

		// w '10 // returns 0
		assertEquals(0, valueOf(10).apply(UnaryOp.NOT).toNumber());
	}
	
	@Test
	public void testMatchLiteral() {
		MVal v = valueOf("FOO");
		// simple matches
		assertTrue(v.matches(0, 1, "FOO"));
		assertTrue(v.matches(0, 1, "FO"));
		assertTrue(v.matches(0, 1, "F"));
		
		// simple non-matches
		assertFalse(v.matches(0, 1, "BAR"));
		assertFalse(v.matches(0, 1, "FOOD"));
		assertFalse(v.matches(0, 1, "foo"));
		
		// offset matches
		assertTrue(v.matches(1, 1, "OO"));
		assertTrue(v.matches(1, 2, "O"));
		
		// offset non-matches
		assertFalse(v.matches(1, 1, "F"));
		assertFalse(v.matches(1, 3, "O"));

		// unbounded matches
		v = valueOf("AAAAAAAAAAAAA");
		assertTrue(v.matches(2, -1, "A"));
		
		// border cases
		v = valueOf("");
		assertTrue(v.matches(0, 1, ""));
		assertFalse(v.matches(0, 1, " "));
		assertFalse(v.matches(-10, 1, ""));
	}
	
	@Test
	public void testMatchCode() {
		
		// check offsets and repeats
		MVal v = valueOf("ABC");
		assertTrue(v.matches(0, 1, MatchOp.A));
		assertTrue(v.matches(0, 3, MatchOp.A));
		assertFalse(v.matches(0, 4, MatchOp.A));
		assertTrue(v.matches(1, 2, MatchOp.A));
		assertTrue(v.matches(2, 1, MatchOp.A));
		assertFalse(v.matches(3, 1, MatchOp.A));
		
		// -1 repeat indicates anything
		assertTrue(v.matches(0, -1, MatchOp.A));
		
		// border/error cases
		assertFalse(v.matches(-1, 1, MatchOp.A));
		assertFalse(v.matches(10, 1, MatchOp.A));
		assertFalse(v.matches(10, 0, MatchOp.A));
		assertFalse(v.matches(0, 999, MatchOp.A));
		
		// check empty string
		v = valueOf("");
		assertFalse(v.matches(0, 1, MatchOp.U));
		assertFalse(v.matches(0, 1, MatchOp.E));
		assertFalse(v.matches(0, 1, MatchOp.A));
		assertFalse(v.matches(0, 1, MatchOp.L));
		assertFalse(v.matches(0, 1, MatchOp.C));
		assertFalse(v.matches(0, 1, MatchOp.N));
		assertFalse(v.matches(0, 1, MatchOp.P));
		
		// check upper case
		v = valueOf("ABC");
		assertTrue(v.matches(0, 3, MatchOp.U));
		assertTrue(v.matches(0, 3, MatchOp.E));
		assertTrue(v.matches(0, 3, MatchOp.A));
		assertFalse(v.matches(0, 3, MatchOp.L));
		assertFalse(v.matches(0, 3, MatchOp.C));
		assertFalse(v.matches(0, 3, MatchOp.N));
		assertFalse(v.matches(0, 3, MatchOp.P));
		
		// check lower case
		v = valueOf("abc");
		assertTrue(v.matches(0, 3, MatchOp.E));
		assertTrue(v.matches(0, 3, MatchOp.A));
		assertTrue(v.matches(0, 3, MatchOp.L));
		assertFalse(v.matches(0, 3, MatchOp.U));
		assertFalse(v.matches(0, 3, MatchOp.C));
		assertFalse(v.matches(0, 3, MatchOp.N));
		assertFalse(v.matches(0, 3, MatchOp.P));
		
		// check digits
		v = valueOf("123");
		assertTrue(v.matches(0, 3, MatchOp.E));
		assertTrue(v.matches(0, 3, MatchOp.N));
		assertFalse(v.matches(0, 3, MatchOp.A));
		assertFalse(v.matches(0, 3, MatchOp.L));
		assertFalse(v.matches(0, 3, MatchOp.U));
		assertFalse(v.matches(0, 3, MatchOp.C));
		assertFalse(v.matches(0, 3, MatchOp.P));
		
		// check control characters
		v = valueOf("\t\n\r\f");
		assertTrue(v.matches(0, 4, MatchOp.E));
		assertTrue(v.matches(0, 4, MatchOp.C));
		assertFalse(v.matches(0, 4, MatchOp.N));
		assertFalse(v.matches(0, 4, MatchOp.A));
		assertFalse(v.matches(0, 4, MatchOp.L));
		assertFalse(v.matches(0, 4, MatchOp.U));
		assertFalse(v.matches(0, 4, MatchOp.P));
		
		// check punctuation
		v = valueOf("!.'~");
		assertTrue(v.matches(0, 4, MatchOp.E));
		assertTrue(v.matches(0, 3, MatchOp.P));
		assertFalse(v.matches(0, 4, MatchOp.N));
		assertFalse(v.matches(0, 4, MatchOp.A));
		assertFalse(v.matches(0, 4, MatchOp.L));
		assertFalse(v.matches(0, 4, MatchOp.U));
		assertFalse(v.matches(0, 4, MatchOp.C));
	}
	
	@Test
	public void testMatchCompoundCodes() {
		MVal v = valueOf("ABC123");
		assertTrue(v.matches(0, 6, MatchOp.U, MatchOp.N));
		assertFalse(v.matches(0, 7, MatchOp.U, MatchOp.N));
		
		v = valueOf("ABC123\n");
		assertTrue(v.matches(0, 6, MatchOp.U, MatchOp.N));
		assertFalse(v.matches(0, 7, MatchOp.U, MatchOp.N));
		
		v = valueOf("ABC1");
		assertTrue(v.matches(0, 1, MatchOp.A));
		assertTrue(v.matches(1, -1, MatchOp.A, MatchOp.N));
	}
	
	private static Number testNum(Object a, Object b, MVal.BinaryOp op) {
		return valueOf(a).apply(op, valueOf(b)).toNumber();
	}
	
	private static String testStr(Object a, Object b, MVal.BinaryOp op) {
		return valueOf(a).apply(op, valueOf(b)).toString();
	}
}
