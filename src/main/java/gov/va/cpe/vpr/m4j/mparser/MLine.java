package gov.va.cpe.vpr.m4j.mparser;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.$P;
import gov.va.cpe.vpr.m4j.mparser.MCmd.MParseException;
import gov.va.cpe.vpr.m4j.mparser.MToken.MExpr;
import gov.va.cpe.vpr.m4j.mparser.MToken.MLineItem;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MLine extends MToken<MLineItem> {
	private String label;
	private int level=0;
	private int index;
	
	public MLine(String line, int idx) {
		// parse some basic info (level + label)
		super(line, 0);
		this.label = MParserUtils.parseRoutineName(line);
		this.level = MParserUtils.determineLineLevel(line);
		this.index = idx;
	}
	
	public int getLevel() {
		return level;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getLabel() {
		return this.label;
	}
	
	public Object eval(MContext ctx) {
		for (MLineItem tok : getTokens()) {
			Object ret = tok.eval(ctx, this);
			if (ret == null || (ret instanceof Boolean && ((Boolean) ret) == Boolean.FALSE)) {
//				System.out.println("False Command eval, quit evaluating line....");
				return null;
			}
		}
		return Boolean.TRUE;
	}
	
	@Override
	public Iterator<MLineItem> iterator() {
		return getTokens().iterator();
	}
	
	/**
	 * parse the line, determines how to organize/arrange the tokens into a higherarchy, essentially an abstract syntax tree (AST)
	 * @throws MParseException 
	 */
	public List<MLineItem> getTokens() {
		// return cached copy if available
		if (this.children != null) return this.children;
		String line = getValue();
		
		// parse the line into string tokens
		List<String> tokens = MParserUtils.tokenize(line, ' ');
		List<MLineItem> ret = new ArrayList<MLineItem>();
		
		// loop through the tokens, keep track of the tokens line offset
		int offset=0;
		for (int i=0; i < tokens.size(); i++) {
			String tokstr = tokens.get(i);
			if (tokstr != null) offset = line.indexOf(tokstr, offset);
			
			// look for non-command tokens first
			if (i == 0 && tokstr != null) {
				// this is a line label (entrypoint)
				ret.add(new MEntryPoint(tokstr, this));
			} else if (tokstr == null) {
				// normal routine line indent/padding 
			} else if (tokstr.matches("\\.+")) {
				// indent token (but we already counted that in the constructor)
			} else if (tokstr.startsWith(";")) {
				// a comment
				ret.add(new MComment(tokstr, this, offset));
			} else {
				// otherwise, its a command token, eagarly fetch the next token to build the command
				String cmdstr = (++i < tokens.size()) ? tokens.get(i) : " ";
				ret.add(resolveCommand(tokstr, cmdstr, offset));
			}
		}
		
		return this.children = ret;
	}
	
	private MCmd resolveCommand(String tokstr, String cmdstr, int offset) {
		// start by constructing a default command (that might not have an exec() implementation)
		MCmd cmd = new MCmd(tokstr, cmdstr, offset, this);
		
		// If we have a more specific class for this command, use introspection to build one.
		String name = cmd.getCommandName();
		Class<? extends MCmd> clz = MCmd.COMMAND_IMPL_MAP.get(name); 
		if (clz != null) {
			try {
				Constructor<? extends MCmd> c = clz.getConstructor(String.class, String.class, int.class, MLine.class);
				cmd = c.newInstance(tokstr, cmdstr, offset, this);
			} catch (Exception ex) {
				throw new IllegalArgumentException("Unable to create command: " + clz);
			}
		}
		
		return cmd;
	}
	
	/**
	 * Convienience method for evaluating a single M line
	 */
	public static Object eval(String mline, MContext ctx) {
		MLine line = new MLine(mline,1);
		return line.eval(ctx);
	}
	
	public static class MComment extends MToken<String> implements MLineItem {
		private MLine line;

		public MComment(String comment, MLine line, int offset) {
			super(comment, offset);
			this.line = line;
		}
		
		@Override
		public MLine getLine() {
			return this.line;
		}
	}

	public static class MEntryPoint extends MToken<MExpr> implements MLineItem {
		private String epname;
		private MLine line;

		public MEntryPoint(String value, MLine line) {
			super(value, 0);
			this.line = line;
			
			// TODO: Something about internal/private entrypoints, need to parse that here....
			
			// split the value into the name, and args list
			this.epname = $P(value, "(", 1);
			String[] argstrs  = $P($P(value, "(", 2), ")", 1).split(",");
			
			// convert the args into arg objects
			List<MExpr> args = new ArrayList<MExpr>();
			int offset = 0;
			for (String arg : argstrs) {
				offset = value.indexOf(arg, offset);
				args.add(new MExpr(arg, offset));
			}
			this.children = args;
		}
		
		public String getEntryPointName() {
			return epname;
		}
		
		@Override
		public MLine getLine() {
			return this.line;
		}
	}
}