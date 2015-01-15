package gov.va.cpe.vpr.m4j.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public abstract class MParserUtils {
	private static Set<String> DEFAULT_DELIMS = new HashSet<String>(Arrays.asList(" "));
	
	/**
	 * Same as tokenize(line, DEFAULT_DELIMS, true, true, false, true)
	 */
	public static final List<String> tokenize(CharSequence line) {
		return tokenize(line, DEFAULT_DELIMS, true, true, false, true, false);
	}
	
	/**
	 * Same as tokenize(line, delim, true, true, false, true)
	 */
	public static final List<String> tokenize(CharSequence line, char delim) {
		return tokenize(line, new HashSet<String>(Arrays.asList(String.valueOf(delim))), true, true, false, true, false);
	}
	
	public static final List<String> tokenizeOps(CharSequence line) {
		return tokenize(line, MCmd.ALL_OPERATORS, true, true, true, true, true);
	}


	/**
	 * M Line tokenizer.  Splits a line into tokens based on specified delimiters.
	 * 
	 * NOTE: even though delims are strings, only handles 1 or 2 character delimiters
	 * 
	 * @param line the line of MUMPS code to tokenize
	 * @param delims the delimiters to use (usually ',' and ' ')
	 * @param quotes if true, ignores token delimiters if they appear inside quotes
	 * @param parens if true, ignores token delimiters if they appear inside parenthesis
	 * @param skipEmpty if true, adjacent delimiters are treated as a single delimiter, if false, you will get a null token inbetween adjacent delimiters
	 * @param comments if true, understands M-style line comments, turns the whole comment into 1 token
	 * @param includeDelims if true, the delimiters will be items in the resulting list as well
	 * 
	 * @return List of string tokens, some values may be null if the token is empty.
	 */
	public static final List<String> tokenize(CharSequence line,
			Set<String> delims, boolean quotes, boolean parens,
			boolean skipEmpty, boolean comments, boolean includeDelims) {
		StringBuilder buff = new StringBuilder();
		List<String> ret = new ArrayList<String>();
		if (line == null) return ret;
		
		int quotelevel=0;
		boolean inquote=false;
		boolean incomment=false;
		
		// parse character by charcter
		for (int i=0; i < line.length(); i++) {
			char c = line.charAt(i); // current char
			char n = ((i+1) < line.length()) ? line.charAt(i+1) : 0;
			
			// do we have a 1 or 2 char delimiter match?
			boolean isdelim1 = delims.contains(String.valueOf(c));
			boolean isdelim2 = delims.contains(String.valueOf(new char[] {c,n}));
			
			if (!inquote && !incomment && quotelevel == 0 && (isdelim1 || isdelim2)) {
				if (isdelim2) i++;
				
				// delimiter character encountered, add buffer to ret
				String tok = (buff.length() == 0) ? null : buff.toString();
				if (tok != null || !skipEmpty) ret.add(tok);
				buff.setLength(0);
				
				// push the delimiter onto the buffer to (if requested)
				if (includeDelims) {
					String delim = (isdelim2) ? String.valueOf(new char[] {c,n}) : String.valueOf(c);
					
					// skip empty indicates we should consume all delims eagerly as one delimter
					/*
					while (skipEmpty && delims.contains(n)) {
						delim += String.valueOf(n);
						n = (++i + 1 < line.length()) ? line.charAt(i+1) : 0;
					}
					*/
					ret.add(delim);
				}
			} else if (parens && c == '(') {
				quotelevel++;
				buff.append(c);
			} else if (parens && c == ')') {
				quotelevel--;
				buff.append(c);
			} else if (quotes && c == '"') {
				// if we are are already in a quote, then peek at the next char
				if (inquote && n == '"') {
					// don't close the quote, its an escaped inner quote, consume one character and go on
					i++;
				} else {
					inquote = !inquote; // start/end of quote
				}
				buff.append(c);
			} else if (comments && c == ';') {
				incomment = true;
				buff.append(c);
			} else {
				buff.append(c);
			}
		}
		
		// done parsing, if the buffer is not empty, add it to the returned results
		String tok = (buff.length() == 0) ? null : buff.toString();
		if (tok != null || !skipEmpty) ret.add(tok);
		return ret;
	}
	
	public static final List<String> infixToPostFix(String expr) {
		Stack<String> stack = new Stack<String>();
		List<String> ret = new ArrayList<String>();
		List<String> toks = MParserUtils.tokenizeOps(expr);
		for (int i=0; i < toks.size(); i++) {
			String tok = toks.get(i);
			
			// if its a nested expression, parse it
			if (tok.startsWith("(") && tok.endsWith(")")) {
				toks.remove(i);
				toks.add(i, ")");
				toks.addAll(i, tokenizeOps(tok.substring(1,tok.length()-1)));
				toks.add(i, "(");
				i--;
				continue;
			}
			
			// If tok is an operator, 
			if (MCmd.ALL_OPERATORS.contains(tok)) {
				// while there is an operator token o(2) at the top of the stack
				// and tok is left-associative and its precedence is less than or equal to that of o(2)
				while (!stack.isEmpty() && !stack.peek().equals("(") && (getOpPrecedence(tok) <= getOpPrecedence(stack.peek()))) {
					ret.add(stack.pop());
				}
				stack.push(tok);
			} else if (tok.equals("(")) {
				stack.push(tok);
			} else if (tok.equals(")")) {
				while (!stack.isEmpty() && !stack.peek().equals("(")) {
					ret.add(stack.pop());
				}
				stack.pop();
			} else {
				ret.add(tok);
			}
		}
		while (!stack.isEmpty()) {
			ret.add(stack.pop());
		}
		
		return ret;
	}
	
	private static final int getOpPrecedence(String op) {
		int ret = 1;
		if (op.equals("=")) {
			ret = 0;
		}
		return ret;
	}
	
	/**
	 * Parse a routine name from the beginning of the line, must be alphanumeric with a starting letter or '%'
	 * @return The name of the matched routine name or null if no routine name was found
	 */
	public static final String parseRoutineName(String line) {
		if (line == null || line.isEmpty() || !(Character.isLetter(line.charAt(0)) || line.charAt(0) == Character.valueOf('%'))) return null;
		
		StringBuilder sb = new StringBuilder();
		sb.append(line.charAt(0));
		for (int i=1; i < line.length(); i++) {
			char c = line.charAt(i);
			
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			} else {
				break;
			}
		}
		return sb.toString();
	}

	public static final String parseStringLiteral(String str, int startAt) {
		if (str == null || startAt < 0 || startAt >= str.length()) return null;
		StringBuilder buff = new StringBuilder();
		boolean inquote = false;
		for (int i=startAt; i < str.length(); i++) {
			char c = str.charAt(i);
			char n = ((i+1) < str.length()) ? str.charAt(i+1) : 0;
			
			// look for a string literal (quoted)
			if (c == '"') {
				if (inquote && n == '"') {
					// don't close the quote, its an escaped inner quote, discard one character and go on
					buff.append(c);
					i++;
					continue;
				} else if (inquote) {
					// end of quote, we are done
					return buff.toString();
				} else {
					// start quote, discard the " and keep going
					inquote = true;
				}
			} else if (inquote) {
				buff.append(c);
			}
		}
		return null;
	}

	
	public static final String parseNumericLiteral(String str, int startAt) {
		if (str == null || startAt < 0 || startAt >= str.length()) return null;
		
		for (int i=startAt; i < str.length(); i++) {
			char c = str.charAt(i);
			char n = ((i+1) < str.length()) ? str.charAt(i+1) : 0;
			
			// recognize start of numeric literal
			if (Character.isDigit(c) || (c == '-' && Character.isDigit(n)) || (c == '.' && Character.isDigit(n))) {
				StringBuilder buff = new StringBuilder();
				
				// eagerly consume all digits and -,.,E's
				while (Character.isDigit(c) || c == '.' || c == '-' || c == 'E' || c == 'e') {
					buff.append(c);
					c = ((i+1) < str.length()) ? str.charAt(++i) : 0; 
				}
				
				// done, we now have our numeric literal token
				return buff.toString();
			}
		}
		return null;
	}
	
	/**
	 * Try to derive a numeric value from the string/value, M has some strange rules about this
	 */
	public static final Number evalNumericValue(Object obj) {
		if (obj == null) return 0;
		if (obj instanceof Number) return (Number) obj;
		String str = obj.toString();
		if (str.isEmpty()) return 0;
		
		boolean isFloat = false;
		float mult = 1f;
		if (str.startsWith("-")) {
			str = str.substring(1);
			mult = -1f;
		}
		if (str.indexOf('E') > 0 || str.indexOf('e') > 0) {
			String[] split = str.split("[eE]");
			str = split[0];
			if (split[1].indexOf('.') > 0) isFloat = true;
			mult *= Math.pow(10, Float.parseFloat(split[1]));
		}

		// now go through the characters, when we encounter the first non-numeric character, ignore the rest
		for (int i=0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '.') {
				isFloat = true;
			} else if (!Character.isDigit(c)) {
				// done
				str = str.substring(0, i);
				break;
			}
		}
		
		// should be a parsable number now
		if (isFloat) {
			return Double.parseDouble(str)*mult;
		} 
		return Integer.parseInt(str)*Math.round(mult);
		
	}
	
	/**
	 * Using a regex, returns all the components of a global or routine reference
	 * @return ret[0] = prefix ($,$$,@,etc.); ret[1] = entrypoint name (if any); ret[2] = routine or global name; ret[3] = args list (if any)
	 */
	public static final String[] parseRef(String str) {
		StringBuilder buff = new StringBuilder();
		String flags = "", name=null, routine=null, subexpr=null;
		
		int parenCount=0;
		for (int i=0; i < str.length(); i++) {
			char c = str.charAt(i);
			
			if (c == '(') {
				if (parenCount == 0) {
					if (name == null) {
						name = nullIfEmpty(buff); 
					} else {
						routine = nullIfEmpty(buff);
					}
					buff.setLength(0);
				} else {
					buff.append(c);
				}
				parenCount++;
			} else if (c == ')') {
				parenCount--;
				if (parenCount == 0) {
					subexpr = nullIfEmpty(buff);
					buff.setLength(0);
				} else {
					buff.append(c);
				}
			} else if (c == '$' || c == '@') {
				if (parenCount == 0) {
					flags += String.valueOf(c);
				} else {
					buff.append(c);
				}
			} else if (c == '^' && parenCount == 0) {
				name = nullIfEmpty(buff);
				buff.setLength(0);
				if (i==0) flags += String.valueOf(c);
			} else {
				buff.append(c);
			}
		}
		
		if (buff.length() > 0) {
			if (name == null) {
				name = nullIfEmpty(buff); 
			} else {
				routine = nullIfEmpty(buff);
			}
		}
		
		return new String[] {(!flags.isEmpty()) ? flags : null, name, routine, subexpr}; 
	}
	
	private static final String nullIfEmpty(StringBuilder buff) {
		if (buff.length() == 0) {
			return null;
		}
		return buff.toString();
	}
	
	/**
	 * @return The indentation level of this line, basically the number of '.' characters found at the lines beginning.
	 */
	public static final int determineLineLevel(String line) {
		if (line == null || line.isEmpty()) return 0;
		int ret = 0;
		for (int i=0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '.') {
				ret++; // one level indented
			} else if (Character.isWhitespace(c)) {
				// keep going
			} else {
				// first non whitespace, we are done
				break;
			}
		}
		return ret;
	}
	
	/**
	 * @return Returns a formatted string of the deep structure of an element, usefull for debugging.
	 */
	public static final String displayStructure(AbstractMToken<?> tok, int maxlevel) {
		StringBuilder sb = new StringBuilder();
		doDisplayStructure(tok, 0, maxlevel, sb);
		return sb.toString();
	}
	
	private static final void doDisplayStructure(AbstractMToken<?> tok, int level, int maxlevel, StringBuilder sb) {
		if (tok == null || level > maxlevel) return;
		sb.append(tok.toString());
		Iterator<?> itr = tok.iterator();
		while (itr.hasNext() && level < maxlevel) {
			AbstractMToken<?> t = (AbstractMToken<?>) itr.next();
			sb.append("\n");
			for (int i=0; i <= level; i++) sb.append("\t");
			doDisplayStructure(t, level+1, maxlevel, sb);
		}
	}

	public static final String displayStructureAlt(MLine line, int maxlevel) {
		StringBuilder sb = new StringBuilder();
		doDisplayStructureAlt(line, line.getIndex(), 0, maxlevel, 0, sb);
		return sb.toString();
	}

	
	private static final void doDisplayStructureAlt(AbstractMToken<?> tok, int curline, int level, int maxlevel, int sumoffset, StringBuilder sb) {
		if (tok == null || level > maxlevel) return;
		String header = fixedWidthStr(curline+"", 3) + ":" + fixedWidthStr(tok.getClass().getSimpleName(), 12) + ":";
		sb.append(header + fixedWidthStr(null, sumoffset+tok.getOffset()) + tok.getValue());
		Iterator<?> itr = tok.iterator();
		while (itr.hasNext() && level < maxlevel) {
			AbstractMToken<?> t = (AbstractMToken<?>) itr.next();
			sb.append("\n");
			doDisplayStructureAlt(t, curline, level+1, maxlevel, sumoffset+tok.getOffset(), sb);
		}
	}

	public static final String fixedWidthStr(String in, int width) {
		char[] chars = new char[width];
		for (int i=0; i < width; i++) {
			chars[i] = (in != null && i < in.length()) ? in.charAt(i) : ' ';
		}
		return new String(chars);
	}
}
