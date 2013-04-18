package gov.va.cpe.vpr.m4j.mparser;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.$P;
import gov.va.cpe.vpr.m4j.lang.MCmdImpl;
import gov.va.cpe.vpr.m4j.lang.MCmdImpl.MParseException;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MLine extends MToken<MToken<?>> {
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
		for (MToken<?> tok : getTokens()) {
			Object ret = tok.eval(ctx);
			if (ret == null || (ret instanceof Boolean && ((Boolean) ret) == Boolean.FALSE)) {
				System.out.println("False Command eval, quit evaluating line....");
				return null;
			}
		}
		return Boolean.TRUE;
	}
	
	@Override
	public Iterator<MToken<?>> iterator() {
		return getTokens().iterator();
	}
	
	/**
	 * parse the line, determines how to organize/arrange the tokens into a higherarchy, essentially an abstract syntax tree (AST)
	 * @throws MParseException 
	 */
	public List<MToken<?>> getTokens() {
		// return cached copy if available
		if (this.children != null) return this.children;
		String line = getValue();
		
		// parse the line into string tokens
		List<String> tokens = MParserUtils.tokenize(line, ' ');
		List<MToken<?>> ret = new ArrayList<MToken<?>>();
		
		// loop through the tokens, keep track of the tokens line offset
		int offset=0;
		for (int i=0; i < tokens.size(); i++) {
			String tokstr = tokens.get(i);
			if (tokstr != null) offset = line.indexOf(tokstr, offset);
			
			// look for non-command tokens first
			if (i == 0 && tokstr != null) {
				// this is a line label (entrypoint)
				ret.add(new MEntryPoint(tokstr));
			} else if (tokstr == null) {
				// normal routine line indent/padding 
			} else if (tokstr.matches("\\.+")) {
				// indent token (but we already counted that in the constructor)
			} else if (tokstr.startsWith(";")) {
				// a comment
				ret.add(new MComment(tokstr, offset));
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
		MCmd cmd = new MCmd(tokstr, cmdstr, offset);
		
		// If we have a more specific class for this command, use introspection to build one.
		String name = cmd.getCommandName();
		Class<? extends MCmd> clz = MCmdImpl.COMMAND_IMPL_MAP.get(name); 
		if (clz != null) {
			try {
				Constructor<? extends MCmd> c = clz.getConstructor(String.class, String.class, int.class);
				cmd = c.newInstance(tokstr, cmdstr, offset);
			} catch (Exception ex) {
				// error, ignoring for now....
			}
		}
		
		return cmd;
	}
	
	public static class MComment extends MToken {
		public MComment(String comment, int offset) {
			super(comment, offset);
		}
	}

	public static class MEntryPoint extends MToken<MArg> {
		private String epname;

		public MEntryPoint(String value) {
			super(value, 0);
			
			// TODO: Something about internal/private entrypoints, need to parse that here....
			
			// split the value into the name, and args list
			this.epname = $P(value, "(", 1);
			String[] argstrs  = $P($P(value, "(", 2), ")", 1).split(",");
			
			// convert the args into arg objects
			List<MArg> args = new ArrayList<MArg>();
			int offset = 0;
			for (String arg : argstrs) {
				offset = value.indexOf(arg, offset);
				args.add(new MArg(arg, offset));
			}
			this.children = args;
		}
		
		public String getEntryPointName() {
			return epname;
		}
	}
	
	public static class MArg extends MToken {
		public MArg(String name, int offset) {
			super(name, offset);
		}
	}
	
}