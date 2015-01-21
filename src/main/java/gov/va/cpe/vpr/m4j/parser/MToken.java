package gov.va.cpe.vpr.m4j.parser;

import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.parser.MCmd.MParseException;

public interface MToken<T> extends Iterable<T> {
	public abstract String getValue();
	public abstract int getOffset();
	public abstract Object eval(M4JProcess ctx, MToken<?> parent) throws MParseException;
	
	public interface MLineItem<T> extends MToken<T> {
		public MLine getLine();
	}
	
	public interface MAssignable {
		public void set(M4JProcess ctx, Object val, MToken<?> parent);
	}

	int size();
}