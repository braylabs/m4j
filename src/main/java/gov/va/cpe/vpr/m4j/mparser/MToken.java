package gov.va.cpe.vpr.m4j.mparser;

import gov.va.cpe.vpr.m4j.MMap;
import gov.va.cpe.vpr.m4j.lang.MCmdImpl;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExprItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class MToken<T> implements Iterable<T> {
	private String value;
	private int offset=0;
	protected List<T> children;
	
	public MToken(String value, int offset) {
		this.value = value;
		this.offset = offset;
	}
	
	public MToken(String value) {
		this(value, 0);
	}

	public String getValue() {
		return this.value;
	}
	
	public int getOffset() {
		return offset;
	}
	
	public void exec(MContext ctx) {
		// does nothing by default
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
				
				// an operator
				if (MCmdImpl.ALL_OPERATORS.contains(tok)) {
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
				MArgList arglist = (args != null) ? new MArgList(args) : null;
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
		
		public abstract static class MTruthValExpr extends MExpr {
			public MTruthValExpr(String tvexpr, int offset) {
				super(tvexpr, offset);
			}
		}
		
		public static class MPostCondTruthValExpr extends MTruthValExpr {
			public MPostCondTruthValExpr(String tvexpr, int offset) {
				super(tvexpr, offset);
			}
		}
	}
	
	// Items ------------------------------------------------------------------
	public abstract static class MExprItem extends MToken<MExprItem> {
		public MExprItem(String value) {
			super(value);
		}
		
		public MExprItem(String value, int offset) {
			super(value, offset);
		}
		
		public Object eval(MContext ctx) {
			if (this.children.size() == 1) {
				return this.children.get(0).eval(ctx);
			}
			return null;
		}

	}
	
	public static class MExprOper extends MExprItem {
		public MExprOper(String value) {
			super(value);
		}
	}
	
	public abstract static class MRef extends MExprItem {
		private MArgList args;
		public MRef(String value, MArgList args) {
			super(value);
			this.args = args;
		}
		
		@Override
		public Iterator<MExprItem> iterator() {
			return (args == null) ? new ArrayList<MExprItem>().iterator() : args.iterator();
		}
		
		public MArgList getArgs() {
			return this.args;
		}
	}
	
	public static class MArgList extends MExprItem {
		public MArgList(String value) {
			super(value);
			getExpressions();
		}
		
		public List<MExprItem> getExpressions() {
			if (this.children != null) return this.children;
			List<MExprItem> ret = new ArrayList<MExprItem>();
			int offset = 0;
			
			// parse the command expression list (comma delimited list of expressions)
			List<String> exprs= MParserUtils.tokenize(getValue(), ',');
			for (String expr : exprs) {
				if (!expr.trim().isEmpty()) {
					offset = getValue().indexOf(expr, offset);
					ret.add(new MExpr(expr, offset));
				}
			}

			return this.children = ret;
		}
				
	}
	
	public static class MRoutineRef extends MRef {
		private String routineName;
		private String entryPoint;

		public MRoutineRef(String value, String entryPoint, String routineName, MArgList args) {
			super(value, args);
			this.entryPoint = entryPoint;
			this.routineName = routineName;
		}
	}
	
	public static class MFxnRef extends MRef {
		private String name;

		public MFxnRef(String value, String name, MArgList args) {
			super(value, args);
			this.name = name;
		}
	}
	
	public static class MGlobalRef extends MRef {
		private String name;

		public MGlobalRef(String value, String name, MArgList args) {
			super(value, args);
			this.name = name;
		}
		
		@Override
		public Object eval(MContext ctx) {
			MMap global = ctx.getGlobal(this.name);
			
			MArgList args = getArgs();
			for (MExprItem expr : args) {
				Object eval = expr.eval(ctx);
				global = global.get(eval);
			}
			
			return global.getValue();
		}
	}
	
	public static class MLocalVarRef extends MRef {
		private String name;

		public MLocalVarRef(String value, String name, MArgList args) {
			super(value, args);
			this.name = name;
		}
	}

	
	// atoms ------------------------------------------------------------------
	
	public static class MExprAtom extends MExprItem {
		public MExprAtom(String value, int offset) {
			super(value);
		}
	}
	
	public static class MExprLiteral extends MExprAtom {
		public MExprLiteral(String value, int offset) {
			super(value, offset);
		}
		
		@Override
		public Object eval(MContext ctx) {
			return getValue();
		}
	}
	
	public static class MExprStrLiteral extends MExprLiteral {
		public MExprStrLiteral(String value, int offset) {
			super(value, offset);
		}
	}
	
	public static class MExprNumLiteral extends MExprLiteral {
		public MExprNumLiteral(String value, int offset) {
			super(value, offset);
		}
	}
}