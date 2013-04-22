package gov.va.cpe.vpr.m4j.mparser;

public interface MToken<T> extends Iterable<T> {
	public abstract String getValue();
	public abstract int getOffset();
	public abstract Object eval(MContext ctx, MToken<?> parent);
	
	public interface MLineItem<T> extends MToken<T> {
		public MLine getLine();
	}
	
	public interface MAssignable {
		public void set(MContext ctx, Object val, MToken<?> parent);
	}
}