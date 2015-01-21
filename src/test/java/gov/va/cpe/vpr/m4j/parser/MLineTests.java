package gov.va.cpe.vpr.m4j.parser;

import static org.junit.Assert.assertEquals;
import gov.va.cpe.vpr.m4j.parser.MToken.MLineItem;

import java.util.List;

import org.junit.Test;

public class MLineTests {

	
	@Test
	public void test() {
		
	}
	
	@Test
	public void testExtraWhitespace() {
		List<MLineItem<?>> toks = new MLine("N %,%1 S %=\"\" F %1=$L(X):-1:1 S %=%_$E(X,%1)").getTokens();
		assertEquals(4, toks.size());

		// same thing with extra spaces should produce the same result
		toks = new MLine("N % , %1 S % = \"\" F %1 = $L(X) : -1 : 1 S % = % _ $E(X, %1)").getTokens();
		assertEquals(4, toks.size());
	}
	
	@Test
	public void testPostFixNotation() {
		List<MLineItem<?>> toks = new MLine("Q:'$D(IN) \"\" Q:$D(SPEC)'>9 IN N %1,%2,%3,%4,%5,%6,%7,%8").getTokens();
		assertEquals(2, toks.size());
		
	}
}
