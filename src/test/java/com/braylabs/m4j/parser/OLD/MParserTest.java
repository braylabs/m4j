package com.braylabs.m4j.parser.OLD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.braylabs.m4j.parser.OLD.MParser.*;

import org.junit.Test;

import com.braylabs.m4j.parser.OLD.MParser.TokenType;

public class MParserTest {
	@Test
	public void testTokenTypeNumLiteral() {
		// matches simple numbers
		assertEquals(TokenType.NUM_LITERAL, getTokenType("+1", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("-1", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("0", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("0.0", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType(".1", null));
		
		// does not match
		assertEquals(TokenType.REF, getTokenType("zero", null));
		assertEquals(TokenType.LINE_INDENT, getTokenType(".", null));
		
		// with exponentials
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1e3", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1e+3", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1e-3", null));
		assertEquals(TokenType.NUM_LITERAL, getTokenType("1.1e3", null));
		
		assertEquals(TokenType.UNKNOWN, getTokenType("1.1e3.1", null));
	}
	
	@Test
	public void testTokenTypeStringLiteral() {
		// only true strings are quoted
		assertTrue(getTokenType("\"\"", null) == TokenType.STR_LITERAL);
		assertTrue(getTokenType("\"FOO\"", null) == TokenType.STR_LITERAL);
		
		// not strings
		assertFalse(getTokenType("'FOO'", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("FOO", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("\"FOO'", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("\"FOO", null) == TokenType.STR_LITERAL);
		assertFalse(getTokenType("", null) == TokenType.STR_LITERAL);
	}
	
	@Test
	public void testTokenTypeCMD() {
		// various case-insensitive
		assertEquals(TokenType.CMD, getTokenType("W", null));
		assertEquals(TokenType.CMD, getTokenType("WRITE", null));
		assertEquals(TokenType.CMD, getTokenType("w", null));
		assertEquals(TokenType.CMD, getTokenType("wRiTe", null));
		
		// with post conditional
		assertEquals(TokenType.CMD, getTokenType("Q:FOO", null));
		
		// non-commands
		assertEquals(TokenType.REF, getTokenType("FOO", null));
	}
	
}
