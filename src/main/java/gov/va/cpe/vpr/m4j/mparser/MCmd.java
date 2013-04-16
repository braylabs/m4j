package gov.va.cpe.vpr.m4j.mparser;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.$P;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExpr.MPostCondTruthValExpr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class MCmd extends MToken<MToken<?>> {
	private String cmdvalue;
	private String cmdname;
	private String cmd;
	
	public MCmd(String cmdname, String cmdvalue, int offset) {
		super(cmdname + " " + ((cmdvalue == null) ? " " : cmdvalue), offset);
		this.cmdname = cmdname;
		this.cmdvalue = cmdvalue;
		this.cmd = $P(this.cmdname, ":", 1).toUpperCase();
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
		
		ret.add(new MCmdName(cmd));
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
	
	public static class MCmdName extends MExprAtom {
		public MCmdName(String value) {
			super(value, 0);
		}
	}
	
	public static class MExprList extends MToken<MExpr> {
		public MExprList(String value) {
			super(value);
			getExpressions();
		}
		
		public List<MExpr> getExpressions() {
			if (this.children != null) return this.children;
			List<MExpr> ret = new ArrayList<MExpr>();
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

		public MCmdI(String cmdname, String cmdvalue, int offset) {
			super(cmdname, cmdvalue, offset);
		}
		
		@Override
		public void exec(MContext ctx) {
			// convert the expression list to postfix for evaluation
			MExprList list = findSubToken(this, MExprList.class, 1);
			
			for (MExpr expr : list) {
				List<MExprItem> items = expr.getExprStack(); 
				for (int i = 0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						String op = ((MExprOper) item).getValue();
						if (op.equals("=")) {
							MExprItem rhs = items.get(i-1);
							MExprItem lhs = items.get(i-2);
							System.out.println("Targ: " + lhs.eval(ctx));
							System.out.println("Val: " + rhs.eval(ctx));
						}
					}
				}
			}
		}
	}
	
	public static class MCmdW extends MCmd {

		public MCmdW(String cmdname, String cmdvalue, int offset) {
			super(cmdname, cmdvalue, offset);
		}
		
		@Override
		public void exec(MContext ctx) {
			// TODO Auto-generated method stub
			super.exec(ctx);
		}
	}
	
	public static class MCmdS extends MCmd {

		public MCmdS(String cmdname, String cmdvalue, int offset) {
			super(cmdname, cmdvalue, offset);
		}
		
		@Override
		public void exec(MContext ctx) {
			// convert the expression list to postfix for evaluation
			MExprList list = findSubToken(this, MExprList.class, 1);
			
			for (MExpr expr : list) {
				List<MExprItem> items = expr.getExprStack(); 
				for (int i = 0; i < items.size(); i++) {
					MExprItem item = items.get(i);
					if (item instanceof MExprOper) {
						String op = ((MExprOper) item).getValue();
						if (op.equals("=")) {
							MExprItem rhs = items.get(i-1);
							MExprItem lhs = items.get(i-2);
							System.out.println("LHS: " + lhs.eval(ctx));
							System.out.println("RHS: " + rhs.eval(ctx));
							
							System.out.println(MParserUtils.displayStructure(item, 10));
						}
					}
				}
			}
		}
	}
	
	
	public static class MCmdQ extends MCmd {

		public MCmdQ(String cmdname, String cmdvalue, int offset) {
			super(cmdname, cmdvalue, offset);
		}
		
		@Override
		public void exec(MContext ctx) {
			// TODO Auto-generated method stub
			super.exec(ctx);
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
}