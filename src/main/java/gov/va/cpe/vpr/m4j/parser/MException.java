package gov.va.cpe.vpr.m4j.parser;

public class MException extends Exception {

	public static class MParseException extends MException {
	}
	
	public static class MSyntaxException extends RuntimeException {
		public MSyntaxException(MLine line, String msg) {
			super(msg);
		}
	}
}
