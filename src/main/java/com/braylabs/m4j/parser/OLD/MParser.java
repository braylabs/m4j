package com.braylabs.m4j.parser.OLD;

import static com.braylabs.m4j.lang.MUMPS.$P;
import static com.braylabs.m4j.parser.MParserUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.braylabs.m4j.lang.MUMPS;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.parser.MCmd;
import com.braylabs.m4j.parser.MParserUtils;

/**
 * Test of an alternative parser/tokenizer
 */
public class MParser {

	private static final String REGEX_NUM_LIT = "^[\\-\\+]?[\\d\\.]+[eE]?[\\-\\+]?[\\d]*$";
	private static final String REGEX_EP_NAME = "^[a-zA-Z]+[a-zA-Z0-9]*$";


	public enum TokenType {
		STR_LITERAL,
		NUM_LITERAL,
		CMD,
		COMMENT,
		REF, // local var, global var, function, etc.
		DELIM, // delimiter (space, etc)
		LINE_INDENT,
		EXPR,
		EXPR_LIST, // comma delimited expression list
		UNKNOWN, PC,
		SPACE,NULL, LINE
	}

	private static class LINE {
		int level;
		String value;
		TOKEN label;
		List<CMD> commands = new ArrayList<>();
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("LINE(" + level + "): " + this.value);
			if (level > 0) sb.append("\n  LEVEL=" + level);
			if (label != null) this.label.toString("\n  ", sb);
			for (CMD cmd : commands) {
				cmd.toString("\n  ", sb);
			}
			return sb.toString();
		}
	}
	
	private static class CMD {
		String name;
		TOKEN postConditional;
		String value;
		List<TOKEN> expressions = null;

		@Override
		public String toString() {
			return toString("", new StringBuilder()).toString();
		}
		
		public StringBuilder toString(String indent, StringBuilder sb) {
			sb.append(indent);
			sb.append("CMD(" + this.name + "):" + this.value);
			if (postConditional != null) postConditional.toString(indent + "  ", sb);
			
			if (expressions != null) {
				for (TOKEN tok : expressions) {
					tok.toString(indent + "  ", sb);
				}
			}
			
			return sb;
		}
	}
	
	public static class TOKEN {
		TokenType type;
		String name;
		String value;
		List<TOKEN> subtokens = new ArrayList<>();
		
		public TOKEN(TokenType type, String value) {
			this.value = value;
			this.type = type;
		}
		
		@Override
		public String toString() {
			return toString("\n", new StringBuilder()).toString();
		}
		
		public StringBuilder toString(String indent, StringBuilder sb) {
			sb.append(indent);
			sb.append(type.name());
			if (this.name != null) sb.append("/" + this.name);
			sb.append(":");
			sb.append(this.value);
			for (TOKEN tok : subtokens) {
				tok.toString(indent + "  ", sb);
			}
			return sb;
		}
	}
	
	public static LINE parseLine(String line) {

		// create line object, set its indent level
		LINE mline = new LINE();
		mline.value = line;
		
		// loop through all tokens in line
		List<String> toks = MParserUtils.tokenize(line, new HashSet<String>(Arrays.asList(" ",":")), true, true, false, true, true);
		
		CMD cmd = null;
		for (int i=0; i < toks.size(); i++) {
			String tok = toks.get(i);
			TokenType type = getTokenType(tok, null);
			
			// if its an entrypoint, add that to the line
			if (i==0 && type == TokenType.REF) {
				mline.label = new TOKEN(TokenType.REF, tok);
				mline.label.subtokens = parseSubTokens(mline.label);
			} else if (type == TokenType.LINE_INDENT) {
				// increment the indent level of this line
				mline.level++;
			} else if (type == TokenType.DELIM || type == TokenType.COMMENT) {
				// skip these
			} else if (type == TokenType.CMD) {
				// start of new command
				
				// append existing command to line (if any)
				if (cmd != null) {
					mline.commands.add(cmd);
					cmd = null;
				}
				cmd = new CMD();
				cmd.name = MUMPS.$P(tok,":",1);
				
				// if there is a post conditional, add it to the command
				String pc = MUMPS.$P(tok,":",2);
				if (pc != null && !pc.isEmpty()) {
					cmd.postConditional = new TOKEN(TokenType.PC, pc);
					cmd.postConditional.subtokens = parseSubTokens(cmd.postConditional);
				}
			} else if (cmd != null) {
				// otherwise, append to current command
				if (cmd.value == null) cmd.value = tok;
				else cmd.value += tok;
			} else {
				// unexpected, error condition
//				throw new RuntimeException("Unexpected token: " + tok);
				System.out.println("Unconsumed TOken: " + tok);
			}
			
		}
		
		// add last cmd to line
		if (cmd != null) {
			mline.commands.add(cmd);
		}
		
		return mline;
	}
	
	public static List<TOKEN> parseSubTokens(TOKEN token) {
		List<TOKEN> ret = new ArrayList<>();
		
		if (token.type == TokenType.CMD) {
			// commands, split comma delimited arguments
			List<String> toks = MParserUtils.tokenize(token.value,',');
			for (String tok : toks) {
				TOKEN mtok = new TOKEN(getTokenType(tok, null), tok);
				ret.add(mtok);
			}
		} else if (token.type == TokenType.REF) {
			// get the arguments (if any)
			String[] ref = MParserUtils.parseRef(token.value);
			if (ref[3] != null) {
				List<String> args = MParserUtils.tokenize(ref[3], ',');
				for (String arg : args) {
					TOKEN t = new TOKEN(getTokenType(arg, null), arg);
					ret.add(t);
				}
			}
		}
		
		return ret;
	}
	
	public static TokenType getTokenType(String tok, M4JProcess proc) {
		// simple things we can find
		if (tok == null) return TokenType.NULL;
		if (tok.equals(" ")) return TokenType.SPACE;
		
		if (tok.startsWith("\"") && tok.endsWith("\"")) {
			// TODO: What about "A"_"B"?
			return TokenType.STR_LITERAL;
		} else if (tok.startsWith(";")) {
			return TokenType.COMMENT;
		} else if (tok.equals(".")) {
			return TokenType.LINE_INDENT;
		} else if (tok.matches(REGEX_NUM_LIT) && !tok.trim().equals(".")) {
			return TokenType.NUM_LITERAL;
		} else if (strContains(tok, MCmd.ALL_OPERATOR_CHARS)) {
			return TokenType.EXPR;
		} else if (MCmd.COMMAND_SET.contains($P(tok,":",1).toUpperCase())) {
			return TokenType.CMD;
		} else if (parseRef(tok) != null) {
			return TokenType.REF;
		} else if (strContains(tok, ',')) {
			return TokenType.EXPR_LIST;
		}
		
		// if there are commas its an expression list
		return TokenType.UNKNOWN;
	}
	
	public static void dumpTokens(String line) {
		List<String> toks = tokenize(line);
		
		System.out.println(line);
		int[] idxs = new int[toks.size()];
		StringBuffer sb = new StringBuffer();
		for (int i=0; i < toks.size(); i++) {
			String tok = toks.get(i);
			int idx = idxs[i] = line.indexOf((tok == null) ? " " : tok, (i == 0) ? 0 : idxs[i-1]);
			
			// append the difference
			if (i > 0) {
				for (int j=0; j < (idx-idxs[i-1]); j++) {
					sb.append(" ");
				}
			}
//			System.out.print(sb.toString());
			System.out.println(idx + ":" + getTokenType(tok, null) + ":" + tok);
			sb.append('|');
		}
	}

	
	public static void main(String[] args) {
		String[] lines = new String[]{
//			"XLFSTR ;ISC-SF/STAFF - String Functions ;12/19/06  09:45",
//			" ;;8.0;KERNEL;**112,120,400,437**;Jul 10, 1995;Build 2",
//			" ; ",
			"UP(X) Q $TR(X,\"abcdefghijklmnopqrstuvwxyz\",\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\")",
			" ;",
			"LOW(X) Q $TR(X,\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\",\"abcdefghijklmnopqrstuvwxyz\")",
			" ;",
			"STRIP(X,Y) Q $TR(X,$G(Y),\"\")",
			" ;",
			"REPEAT(X,Y) ;",
			" N N",
			" N % Q:'$D(X) \"\" I $L(X)*$G(Y)>245 Q \"\"",
			" S %=\"\",$P(%,X,$G(Y)+1)=\"\"",
			" Q %",
			" ;",
		};

		
		for (String line : lines) {
			LINE l = parseLine(line);
			System.out.println(l);
		}
	}
}
