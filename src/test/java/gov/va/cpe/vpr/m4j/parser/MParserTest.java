package gov.va.cpe.vpr.m4j.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static gov.va.cpe.vpr.m4j.parser.MParser.*;
import gov.va.cpe.vpr.m4j.parser.MParser.TokenType;

import org.junit.Test;

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
		assertEquals(TokenType.UNKNOWN, getTokenType("zero", null));
		assertEquals(TokenType.UNKNOWN, getTokenType(".", null));
		
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
		assertEquals(TokenType.COMMAND, getTokenType("W", null));
		assertEquals(TokenType.COMMAND, getTokenType("WRITE", null));
		assertEquals(TokenType.COMMAND, getTokenType("w", null));
		assertEquals(TokenType.COMMAND, getTokenType("wRiTe", null));
		
		// with post conditional
		assertEquals(TokenType.COMMAND, getTokenType("Q:FOO", null));
		
		// non-commands
		assertEquals(TokenType.UNKNOWN, getTokenType("FOO", null));
	}
	
}
