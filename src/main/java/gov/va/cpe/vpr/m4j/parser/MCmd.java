package gov.va.cpe.vpr.m4j.parser;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.$P;
import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExpr.MPostCondTruthValExpr;
import gov.va.cpe.vpr.m4j.parser.MToken.MLineItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MCmd extends AbstractMToken<MToken<?>> implements MLineItem<MToken<?>> {
	
	public static final Set<String> COMMAND_SET = Collections
			.unmodifiableSet(new HashSet<String>(Arrays.asList("B", "C", "D",
					"E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "Q",
					"R", "S", "TC", "TRE", "TRO", "TS", "U", "V", "W", "X")));
	
	public static final Set<String> ARITHMETIC_OPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("+", "-", "*", "**", "/", "\\", "#")));
	
	public static final Set<String> LOGICAL_OPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("'", "&", "!")));
	public static final Set<String> NUMERIC_REL_OPS = Collections
			.unmodifiableSet(new HashSet<String>(Arrays.asList(">", "<", "=",
					"'>", "'<", ">=", "<=", "'=")));
	
	public static final Set<String> STRING_REL_OPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("=", "[", "]", "]]", "'[", "']", "']]", "'=")));
	
	public static final Set<String> STRING_OPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("_")));
	public static final Set<String> PATTERN_MATCH_OPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("")));
	
	public static final Set<Character> ALL_OPERATOR_CHARS = Collections
			.unmodifiableSet(new HashSet<Character>(Arrays.asList('+', '-',
					'*', '/', '\\', '#', '\'', '&', '!', '>', '<', '=', '[',
					']', '_')));
	
	public static Set<String> ALL_OPERATORS = null;
	public static Map<String, Class<? extends MCmd>> COMMAND_IMPL_MAP = null;
	
	static {
		Set<String> all = new HashSet<String>();
		all.addAll(ARITHMETIC_OPS);
		all.addAll(LOGICAL_OPS);
		all.addAll(NUMERIC_REL_OPS);
		all.addAll(STRING_REL_OPS);
		all.addAll(STRING_OPS);
		all.addAll(PATTERN_MATCH_OPS);
		ALL_OPERATORS = Collections.unmodifiableSet(all);
		
		Map<String, Class<? extends MCmd>> impl = new HashMap<String, Class<? extends MCmd>>();
		impl.put("I", MCmd.MCmdI.class);
		impl.put("IF", MCmd.MCmdI.class);
		impl.put("F", MCmd.MCmdF.class);
		impl.put("FOR", MCmd.MCmdF.class);

		impl.put("W", MCmd.MCmdW.class);
		impl.put("WRITE", MCmd.MCmdW.class);
		impl.put("S", MCmd.MCmdS.class);
		impl.put("SET", MCmd.MCmdS.class);
		impl.put("Q", MCmd.MCmdQ.class);
		impl.put("QUIT", MCmd.MCmdQ.class);
		COMMAND_IMPL_MAP = Collections.unmodifiableMap(impl);
	}
	
	
	private String cmdvalue;
	private String cmdname;
	private String cmd;
	private MLine line;
	
	public MCmd(String cmdname, String cmdvalue, int offset, MLine line) {
		super(cmdname + " " + ((cmdvalue == null) ? " " : cmdvalue), offset);
		this.line = line;
		this.cmdname = cmdname;
		this.cmdvalue = cmdvalue;
		this.cmd = $P(this.cmdname, ":", 1).toUpperCase();
	}
	
	public MLine getLine() {
		return line;
	}
	
	public List<MToken<?>> getTokens() {
		if (this.children != null) return this.children;
		List<MToken<?>> ret = new ArrayList<MToken<?>>();
		int offset = 0;
		
		// look for a postconditional expr
		this.cmd = $P(this.cmdname, ":", 1).toUpperCase();
		String pce = $P(this.cmdname, ":", 2);
		if (!pce.isEmpty()) {
			offset = getValue().indexOf(pce, offset);
		}
		
		if (pce != null && !pce.isEmpty()) {
			ret.add(new MPostCondTruthValExpr(pce, offset));
		}
		
		ret.add(new MExprList(this.cmdvalue));
		return this.children = ret;
	}
	
	public String getCommandName() {
		return this.cmd;
	}
	
	@Override
	public Iterator<MToken<?>> iterator() {
		return getTokens().iterator();
	}
	
	public static class MExprList extends MExprItem {
		public MExprList(String value) {
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
	
	public static class MCmdI extends MCmd {

		public MCmdI(String cmdname, String cmdvalue, int offset, MLine line) {
			super(cmdname, cmdvalue, offset, line);
		}
		
		@Override
		public Object eval(M4JProcess ctx, MToken<?> parent) {
			// convert the expression list to postfix for evaluation
			MExprList list = findSubToken(this, MExprList.class);
			
			for (MExprItem expr : list) {
				List<MExprItem> items = ((gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExpr) expr).getExprStack(); 
				for (int i = 0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						String op = ((MExprOper) item).getValue();
						if (op.equals("=")) {
							MExprItem rhs = items.get(i-1);
							MExprItem lhs = items.get(i-2);
							
							Object v1 = rhs.eval(ctx, this), v2 = lhs.eval(ctx, this);
//							System.out.println("Testing: " + v1 + "=" + v2);
							if ((v1 == null && v2 == null) || (v1 != null && v2 != null && v1.equals(v2))) {
								return Boolean.TRUE;
							}
						}
					}
				}
			}
			return Boolean.FALSE;
		}
	}
	
	public static class MCmdW extends MCmd {

		public MCmdW(String cmdname, String cmdvalue, int offset, MLine line) {
			super(cmdname, cmdvalue, offset, line);
		}
		
		@Override
		public Object eval(M4JProcess ctx, MToken<?> parent) {
			// convert the expression list to postfix for evaluation
			MExprList list = findSubToken(this, MExprList.class);
			
			for (MExprItem expr : list) {
				List<MExprItem> items = ((gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExpr) expr).getExprStack();
				
				for (int i=0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						// pop it and lhs and rhs
						String op = items.remove(i--).getValue();
						MExprItem rhs = (items.size() > i && i >= 0) ? items.remove(i--) : null;
						MExprItem lhs = (items.size() > i && i >= 0) ? items.remove(i--) : null;
						
						if (op.equals("_") && rhs != null && lhs != null) {
							String val = lhs.eval(ctx, this).toString() + rhs.eval(ctx, this).toString();
							items.add(i+1, new MExprStrLiteral(val, -1));
						} else if (op.equals("!")) {
							// in the write command, this isn't really an operator, its a newline
							items.add(i+1, new MExprStrLiteral("\n", -1));
						} else {
							throw new RuntimeException("Unknown Operator: " + item);
						}
					}
				}
				
				// if there is only one item left on the stack, print that
				if (items.size() == 1) {
					ctx.getOutputStream().print(items.get(0).eval(ctx, this));
				} else {
					throw new IllegalArgumentException("Unballanced statement, remaining evaluation stack: " + items);
				}
			}
			
			return Boolean.TRUE;
		}
	}
	
	public static class MCmdS extends MCmd {

		public MCmdS(String cmdname, String cmdvalue, int offset, MLine line) {
			super(cmdname, cmdvalue, offset, line);
		}
		
		@Override
		public Object eval(M4JProcess ctx, MToken<?> parent) {
			// convert the expression list to postfix for evaluation
			MExprList list = findSubToken(this, MExprList.class);
			
			for (MExprItem expr : list) {
				List<MExprItem> items = ((gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExpr) expr).getExprStack();
				for (int i = 0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						String op = items.remove(i--).getValue();
						MExprItem rhs = (items.size() > i && i >= 0) ? items.remove(i--) : null;
						MExprItem lhs = (items.size() > i && i >= 0) ? items.remove(i--) : null;
						if (op.equals("=")) {
							if (lhs instanceof MAssignable) {
								((MAssignable) lhs).set(ctx, rhs.eval(ctx, this), this);
							}
						} else if (op.equals("+")) {
							Number val1 = MParserUtils.evalNumericValue(lhs.eval(ctx, this));
							Number val2 = MParserUtils.evalNumericValue(rhs.eval(ctx, this));
							double newval = val1.doubleValue()+val2.doubleValue();
							if (newval % 1 == 0) {
								items.add(i+1,new MExprNumLiteral(((int) newval) + "", -1));
							} else {
								items.add(i+1,new MExprNumLiteral(newval + "", -1));
							}
						} else {
							throw new IllegalStateException("Unknown Operator: " + op);
						}
					}
				}
			}
			
			return Boolean.TRUE;
		}
	}
	
	public static class MCmdF extends MCmd {

		public MCmdF(String cmdname, String cmdvalue, int offset, MLine line) {
			super(cmdname, cmdvalue, offset, line);
		}
		
		@Override
		public Object eval(M4JProcess ctx, MToken<?> parent) {
			// convert the expression list to postfix for evaluation
			MExprList list = findSubToken(this, MExprList.class);

			Integer incVal = null;
			Integer incLimit = null;
			MAssignable iteratorVal = null;
			
			// loop through the expressions to initalize the loop
			for (MExprItem expr : list) {
				List<MExprItem> items = ((gov.va.cpe.vpr.m4j.parser.AbstractMToken.MExpr) expr).getExprStack();
				for (int i = 0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						String op = ((MExprOper) item).getValue();
						if (op.equals("=")) {
							MExprItem rhs = items.get(i-1);
							MExprItem lhs = items.get(i-2);
							if (lhs instanceof MAssignable) {
//								System.out.println("Initalizing: " + lhs + " TO " + rhs.eval(ctx, this));
								iteratorVal = (MAssignable) lhs;
								iteratorVal.set(ctx, rhs.eval(ctx, this), this);
							}
						}
					} else if (item instanceof MExprParam) {
						// TODO:this needs to be able to handle floating point values
						if (incVal == null) {
							incVal = Integer.parseInt(item.eval(ctx, this).toString());
						} else if (incLimit == null) {
							incLimit = Integer.parseInt(item.eval(ctx, this).toString());
						}
						items.remove(i--);
					}
				}
			}
			
			// now run the rest of the line x number of times
			MLine line = (MLine) parent;
			List<MLineItem<?>> lineRest = line.getTokens();
			int forLoopPos = line.getTokens().indexOf(this);
			boolean stop = false;
			for (;;) {
				for (int i=forLoopPos+1; i != -1 && i < lineRest.size();i++ ) {
					MLineItem<?> tok = lineRest.get(i);
					if (tok instanceof MCmd) {
						Object ret = tok.eval(ctx, this);
						if (ret != null && (ret instanceof Boolean && ((Boolean) ret) == Boolean.FALSE)) {
							stop=true;
							break;
						}
					}
				}
				if (stop) break;
				
				
				// increment if requested
				if (iteratorVal != null && incVal != null) {
					Integer intval = Integer.parseInt(((MToken<?>)iteratorVal).eval(ctx, this).toString());
					if (incLimit != null && intval < incLimit) {
						iteratorVal.set(ctx, intval+incVal, this);
					} else {
						stop = true;
						break;
					}
				}

			}
			
			return Boolean.FALSE; // dont run anymore
		}
	}
	
	
	
	public static class MCmdQ extends MCmd {

		public MCmdQ(String cmdname, String cmdvalue, int offset, MLine line) {
			super(cmdname, cmdvalue, offset, line);
		}
		
		@Override
		public Object eval(M4JProcess ctx, MToken<?> parent) {
			
			MPostCondTruthValExpr tvexpr = findSubToken(this, MPostCondTruthValExpr.class);
			if (tvexpr != null) {
				Object result = tvexpr.eval(ctx, this);
				if (result != null && result instanceof Boolean) {
					Boolean ret = (Boolean) result;
					return (ret == Boolean.TRUE) ? Boolean.FALSE : Boolean.TRUE;
				}
			}
			return Boolean.FALSE; // indicate to stop processing...
		}
	}
	
	public static class MParseException extends Exception {
		private static final long serialVersionUID = 1L;
		private MToken<?> tok;
		
		public MParseException(MToken<?> tok, String msg) {
			super(msg);
			this.tok = tok;
		}
		
		public MToken<?> getToken() {
			return tok;
		}
	}
	
	private static final <T extends MToken<?>> T findSubToken(MCmd tok, Class<T> clazz) {
		return findSubToken(tok, clazz, -1);
	}
	
	private static final <T extends MToken<?>> T findSubToken(MCmd tok, Class<T> clazz, int pos) {
		T ret = null;
		if (pos >= 0 && pos < tok.getTokens().size()) {
			MToken<?> t = tok.getTokens().get(pos);
			if (t != null && clazz.isAssignableFrom(t.getClass())) {
				ret = (T) t;
			}
		} else if (pos == -1) {
			for (MToken<?> t : tok.getTokens()) {
				if (clazz.isAssignableFrom(t.getClass())) {
					return (T) t;
				}
			}
		}
		return ret;
	}
}