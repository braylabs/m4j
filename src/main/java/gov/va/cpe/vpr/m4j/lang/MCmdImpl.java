package gov.va.cpe.vpr.m4j.lang;

import gov.va.cpe.vpr.m4j.mparser.MCmd;
import gov.va.cpe.vpr.m4j.mparser.MCmd.MCmdName;
import gov.va.cpe.vpr.m4j.mparser.MToken;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExpr.MPostCondTruthValExpr;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExpr.MTruthValExpr;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class MCmdImpl {
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
		impl.put("W", MCmd.MCmdW.class);
		impl.put("WRITE", MCmd.MCmdW.class);
		impl.put("S", MCmd.MCmdS.class);
		impl.put("SET", MCmd.MCmdS.class);
		impl.put("Q", MCmd.MCmdQ.class);
		impl.put("QUIT", MCmd.MCmdQ.class);
		COMMAND_IMPL_MAP = Collections.unmodifiableMap(impl);
	}
	
	public static final void exec(MCmd tok) {
		MCmdName name = findSubToken(tok, MCmdName.class, 0);
		String cmd = (name != null) ? name.getValue() : "";
		if (cmd.equalsIgnoreCase("w") || cmd.equalsIgnoreCase("write")) {
			W(tok);
		}
		
		// TODO: error if comment is not last token of line?
		// if (i != tokens.size()-1) throw new MParseException(this, "Expected the comment to be the last line token!?!");

	}
	
	public static final void W(MCmd tok) {
		MPostCondTruthValExpr tvexpr = findSubToken(tok, MPostCondTruthValExpr.class, 0);
		if (tvexpr != null) {
			// TODO: test precondition
		}
		
		for (MToken t : tok.getTokens()) {
			if (t instanceof MTruthValExpr) {
				continue;
			} else if (t.getValue().equals("!")) {
				System.out.println();
				continue;
			} else {
				System.out.print(t.getValue());
			}
		}
	}

	
	private static final <T extends MToken<?>> T findSubToken(MCmd tok, Class<T> clazz, int pos) {
		T ret = null;
		if (pos >= 0 && pos < tok.getTokens().size()) {
			MToken<?> t = tok.getTokens().get(pos);
			if (t != null && clazz.isAssignableFrom(t.getClass())) {
				ret = (T) t;
			}
		}
		return ret;
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
}
