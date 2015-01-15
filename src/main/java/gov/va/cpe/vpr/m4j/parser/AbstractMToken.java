package gov.va.cpe.vpr.m4j.parser;

import gov.va.cpe.vpr.m4j.global.MVar;
import gov.va.cpe.vpr.m4j.lang.MProcess;
import gov.va.cpe.vpr.m4j.parser.MCmd.MExprList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractMToken<T> implements Iterable<T>, MToken<T> {
	private String value;
	private int offset=0;
	protected List<T> children;
	
	protected AbstractMToken(String value, int offset) {
		this.value = value;
		this.offset = offset;
	}
	
	protected AbstractMToken(String value) {
		this(value, 0);
	}

	@Override
	public String getValue() {
		return this.value;
	}
	
	@Override
	public int getOffset() {
		return offset;
	}
	
	@Override
	public Object eval(MProcess ctx, MToken<?> parent) {
		// does nothing by default
		return null;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + ":" + getValue();
	}
	
	@Override
	public Iterator<T> iterator() {
		if (this.children == null) return new ArrayList<T>().iterator();
		return this.children.iterator();
	}
	
	public static class MExpr extends MExprItem {

		public MExpr(String name, int offset) {
			super(name, offset);
			getExprItems();
		}
		
		/**
		 * split the expression into atoms (operators and atoms)
		 */
		public List<MExprItem> getExprItems() {
			if (this.children != null) return this.children;
			return this.children = parse(MParserUtils.tokenizeOps(getValue()));
		}
		
		public List<MExprItem> getExprStack() {
			return parse(MParserUtils.infixToPostFix(getValue()));
		}
		
		public static List<MExprItem> parse(List<String> toks) {
			List<MExprItem> ret = new ArrayList<MExprItem>();
			for (int i=0; i < toks.size(); i++) {
				String tok = toks.get(i);
				
				// look for MExprParams
				List<String> params = MParserUtils.tokenize(tok, ':');
				for (int j=1; j < params.size(); j++) {
					ret.add(new MExprParam(params.get(j)));
				}
				
				// an operator
				if (MCmd.ALL_OPERATORS.contains(tok)) {
					ret.add(new MExprOper(tok));
					continue;
				}
				
				// try to find a string literal
				String strlit = MParserUtils.parseStringLiteral(tok, 0);
				if (tok.startsWith("\"") && strlit != null) {
					ret.add(new MExprStrLiteral(strlit, -1));
					continue;
				}
				
				// try to find a numerical literal
				String numlit = MParserUtils.parseNumericLiteral(tok, 0);
				if (numlit != null && tok.indexOf(numlit) == 0) {
					ret.add(new MExprNumLiteral(numlit, -1));
					continue;
				}
				
				// otherwise, its a routine/function/variable reference
				String[] parts = MParserUtils.parseRef(tok);
				String flags = parts[0], name = parts[1], routine = parts[2], args = parts[3];
				MExprList arglist = (args != null) ? new MExprList(args) : null;
				if (flags != null && flags.equals("$$")) {
					// routine ref
					ret.add(new MRoutineRef(tok, name, routine, arglist));
				} else if (flags != null && flags.equals("$")) {
					// function ref
					ret.add(new MFxnRef(tok, name, arglist));
				} else if (flags != null && flags.equals("^")) {
					// global ref
					ret.add(new MGlobalRef(tok, name, arglist));
				} else if (routine != null) {
					// routine ref (ex: D FOO^BAR)
					ret.add(new MRoutineRef(tok, name, routine, arglist));
				} else {
					// local var ref
					ret.add(new MLocalVarRef(tok, name, arglist));
				}

			}
			
			return ret;
		}
		
		public static class MPostCondTruthValExpr extends MExpr {
			public MPostCondTruthValExpr(String tvexpr, int offset) {
				super(tvexpr, offset);
			}
			
			@Override
			public Object eval(MProcess ctx, MToken<?> parent) {
				List<MExprItem> items = getExprStack();
				
				for (int i=0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						// pop it and lhs and rhs
						String op = items.remove(i--).getValue();
						MExprItem rhs = (items.size() > i && i >= 0) ? items.remove(i--) : null;
						MExprItem lhs = (items.size() > i && i >= 0) ? items.remove(i--) : null;
						
						if (op.equals(">")) {
							Number val1 = MParserUtils.evalNumericValue(rhs.eval(ctx, this));
							Number val2 = MParserUtils.evalNumericValue(lhs.eval(ctx, this));
							if ( val2.doubleValue() > val1.doubleValue()) {
								return Boolean.TRUE;
							}
						} else {
							throw new RuntimeException("Unknown Operator: " + item);
						}
					}
				}
				
				return Boolean.FALSE;
			}
		}
	}
	
	// Items ------------------------------------------------------------------
	public abstract static class MExprItem extends AbstractMToken<MExprItem> {
		public MExprItem(String value) {
			super(value);
		}
		
		public MExprItem(String value, int offset) {
			super(value, offset);
		}
		
		@Override
		public Object eval(MProcess ctx, MToken<?> parent) {
			if (this.children != null && this.children.size() == 1) {
				return this.children.get(0).eval(ctx, parent);
			}
			return null;
		}

	}
	
	/**
	 * This is a expression param, such as the 5 and 10 in F I=1:5:10.  Its really just a container for another MExpr
	 */
	public static class MExprParam extends MExpr {
		public MExprParam(String value) {
			super(value,-1);
		}
	}
	
	public static class MExprOper extends MExprItem {
		public MExprOper(String value) {
			super(value);
		}
	}
	
	public abstract static class MRef extends MExprItem {
		private MExprList args;
		public MRef(String value, MExprList args) {
			super(value);
			this.args = args;
		}
		
		@Override
		public Iterator<MExprItem> iterator() {
//			return (args == null) ? new ArrayList<MExpr>().iterator() : args.iterator();
			return args.iterator();
		}
		
		public MExprList getArgs() {
			return this.args;
		}
	}
	
	public static class MRoutineRef extends MRef {
		private String routineName;
		private String entryPoint;

		public MRoutineRef(String value, String entryPoint, String routineName, MExprList args) {
			super(value, args);
			this.entryPoint = entryPoint;
			this.routineName = routineName;
		}
	}
	
	public static class MFxnRef extends MRef {
		private String name;

		public MFxnRef(String value, String name, MExprList args) {
			super(value, args);
			this.name = name;
		}
	}
	
	public static class MGlobalRef extends MRef implements MAssignable {
		private String name;

		public MGlobalRef(String value, String name, MExprList args) {
			super(value, args);
			this.name = name;
		}
		
		@Override
		public Object eval(MProcess ctx, MToken<?> parent) {
			MVar global = ctx.getGlobal(this.name);
			
			MExprList args = getArgs();
			if (args != null) {
				for (MExprItem expr : args) {
					Object eval = expr.eval(ctx, parent);
					global = global.get((Comparable<?>) eval);
				}
			}
			
			return global.val();
		}
		
		@Override
		public void set(MProcess ctx, Object val, MToken<?> parent) {
			MVar var = ctx.getGlobal(this.name);
			
			MExprList args = getArgs();
			if (args != null) {
				for (MExprItem expr : args) {
					Object eval = expr.eval(ctx, parent);
					var = var.get((Comparable<?>) eval);
				}
			}
			
			var.set(val);
		}
	}
	
	public static class MLocalVarRef extends MRef implements MAssignable {
		private String name;

		public MLocalVarRef(String value, String name, MExprList args) {
			super(value, args);
			this.name = name;
		}

		@Override
		public Object eval(MProcess ctx, MToken<?> parent) {
			MVar local = ctx.getLocal(this.name);
			
			MExprList args = getArgs();
			if (args != null) {
				for (MExprItem expr : args) {
					Object eval = expr.eval(ctx, parent);
					local = local.get((Comparable<?>) eval);
				}
			}
			
			return local.val();
		}
		
		@Override
		public void set(MProcess ctx, Object val, MToken<?> parent) {
			MVar var = ctx.getLocal(this.name);
			
			MExprList args = getArgs();
			if (args != null) {
				for (MExprItem expr : args) {
					Object eval = expr.eval(ctx, parent);
					var = var.get((Comparable<?>) eval);
				}
			}
			var.set(val);
		}
	}

	
	// atoms ------------------------------------------------------------------
	
	public abstract static class MExprAtom extends MExprItem {
		public MExprAtom(String value, int offset) {
			super(value);
		}
		
		@Override
		public Object eval(MProcess ctx, MToken<?> parent) {
			return getValue();
		}
	}
	
	public static class MExprStrLiteral extends MExprAtom {
		public MExprStrLiteral(String value, int offset) {
			super(value, offset);
		}
	}
	
	public static class MExprNumLiteral extends MExprAtom {
		public MExprNumLiteral(String value, int offset) {
			super(value, offset);
		}
		
		@Override
		public Object eval(MProcess ctx, MToken<?> parent) {
			return MParserUtils.evalNumericValue(getValue());
		}
	}
}