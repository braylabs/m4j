package gov.va.cpe.vpr.m4j.parser;

import static gov.va.cpe.vpr.m4j.lang.MUMPS.$P;
import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.parser.MCmd.MCmdQ;
import gov.va.cpe.vpr.m4j.parser.MCmd.MParseException;
import gov.va.cpe.vpr.m4j.parser.MToken.MLineItem;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class MLine extends AbstractMToken<MLineItem<?>> {
	private String label;
	private int level=0;
	private MRoutine parent;

	public MLine(String line) {
		this(line, 0, null);
	}

	public MLine(String line, int idx, MRoutine parent) {
		// parse some basic info (level + label)
		super(line, idx);
		this.parent = parent;
		this.level = MParserUtils.determineLineLevel(line);
		
		// look for a line label (only if part of a routine)
		if (parent != null) this.label = MParserUtils.parseRoutineName(line);
	}
	
	public int getLevel() {
		return level;
	}
	
	public String getLabel() {
		return this.label;
	}
	
	public MRoutine getParent() {
		return parent;
	}

	public Object eval2(M4JProcess ctx, MToken<?> parent) throws MParseException {
		// evaluate token by token
		
		List<String> toks = MParserUtils.tokenize(getValue());
		
		for (String tok : toks) {
			if (MCmd.COMMAND_SET.contains(tok.toUpperCase())) {
				
			}
		}
		
		
		
		return null;
	}
	
	@Override
	public Object eval(M4JProcess ctx, MToken<?> parent) throws MParseException {
		try {
			for (MLineItem<?> tok : getTokens()) {
				Object ret = tok.eval(ctx, this);
				if (ret == null) {
					// no return value, keep going.
				} else if (ret instanceof MCmdQ.QuitReturn) {
					return ((MCmdQ.QuitReturn) ret).getValue();
				}
			}
		} catch (RuntimeException ex) {
			System.err.println("Error evaluating M Line (" + getOffset() + "): " + getValue());
			ex.printStackTrace();
		}
		return Boolean.TRUE;
	}
	
	@Override
	public Iterator<MLineItem<?>> iterator() {
		return getTokens().iterator();
	}
	
	/**
	 * parse the line, determines how to organize/arrange the tokens into a hierarchy, essentially an abstract syntax tree (AST)
	 * @throws MParseException 
	 */
	public List<MLineItem<?>> getTokens() {
		// return cached copy if available
		if (this.children != null) return this.children;
		String line = getValue();
		
		// parse the line into string tokens
		List<String> tokens = MParserUtils.tokenize(line);
		List<MLineItem<?>> ret = new ArrayList<MLineItem<?>>();
		
		// loop through the tokens, keep track of the tokens line offset
		int offset=0;
		for (int i=0; i < tokens.size(); i++) {
			String tokstr = tokens.get(i);
			if (tokstr != null) offset = line.indexOf(tokstr, offset);
			
			// look for non-command tokens first
			if (i == 0 && tokstr != null && this.label != null) {
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
				// otherwise, its a command token, eagerly fetch the next token to build the command
				String cmdstr = (++i < tokens.size()) ? tokens.get(i) : " ";
				MCmd cmd = resolveCommand(tokstr, cmdstr, offset);
				if (cmd == null) throw new MException.MSyntaxException(this, "Unable to resolve token as command: " + tokstr);
				ret.add(cmd);
			}
		}
		
		return this.children = ret;
	}
	
	private MCmd resolveCommand(String tokstr, String cmdstr, int offset) {
		// start by constructing a default command (that might not have an exec() implementation)
		MCmd cmd = new MCmd(tokstr, cmdstr, offset, this);
		
		// If we have a more specific class for this command, use introspection to build one.
		String name = cmd.getCommandName();
		
		// if its not a real command, return null
		if (!MCmd.COMMAND_SET.contains(name)) return null;
		
		// if the command has a specific implementation class, use that
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
	
	public static class MComment extends AbstractMToken<String> implements MLineItem<String> {
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

	public static class MEntryPoint extends AbstractMToken<MLocalVarRef> implements MLineItem<MLocalVarRef> {
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
			List<MLocalVarRef> args = new ArrayList<>();
			int offset = 0;
			for (String arg : argstrs) {
				offset = value.indexOf(arg, offset);
				args.add(new MLocalVarRef(arg, arg, null));
			}
			this.children = args;
		}
		
		public String getEntryPointName() {
			return epname;
		}
		
		public List<MLocalVarRef> getVars() {
			return this.children;
		}
		
		@Override
		public MLine getLine() {
			return this.line;
		}
		
		@Override
		public Object eval(M4JProcess ctx, MToken<?> parent) throws MParseException {
			// auto NEW the variables and copy from current values
			
			for (MLocalVarRef ref : this.children) {
				System.out.println(ref);
			}
			
			// TODO Auto-generated method stub
			return super.eval(ctx, parent);
		}
	}
}