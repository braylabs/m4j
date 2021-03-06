package com.braylabs.m4j.parser;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
	
	/**
	 * Parse a routine name/entry point from the beginning of the line, must be alphanumeric with a starting letter or '%'
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
		if (obj instanceof Boolean) return ((Boolean) obj) == Boolean.TRUE ? 1 : 0;
		String str = obj.toString();
		if (str.isEmpty()) return 0;
		
		boolean isFloat = false;
		float mult = 1f;
		if (str.startsWith("-")) {
			str = str.substring(1);
			mult = -1f;
		} else if (str.startsWith("+")) {
			str = str.substring(1);
		}
		if (str.indexOf('E') > 0 || str.indexOf('e') > 0) {
			String[] split = str.split("[eE]");
			if (split.length == 2 && split[0].matches("[0-9\\.]+") && split[1].matches("[0-9\\-]+")) {
				str = split[0];
				if (split[1].indexOf('.') > 0) isFloat = true;
				mult *= Math.pow(10, Float.parseFloat(split[1]));
			}
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
		
		// empty then not a string
		if (str.isEmpty() || str.equals(".")) return new Integer(0);
		
		// should be a parsable number now
		if (isFloat) {
			return Double.parseDouble(str)*mult;
		} 
		
		// parse and return as integer
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
			} else if (c == '$' || c == '@' || c == '.') {
				// valid prefix/flag
				if (parenCount == 0) {
					flags += String.valueOf(c);
				} else {
					buff.append(c);
				}
			} else if (c == '%' && i <= 1) {
				// valid only as the character 1 or 2 of a local var
				buff.append(c);
			} else if (c == '^' && parenCount == 0) {
				// valid prefix and entrypoint indicator
				name = nullIfEmpty(buff);
				buff.setLength(0);
				if (i==0) flags += String.valueOf(c);
			} else if (parenCount == 0 && Character.isDigit(c) && buff.length() == 0) {
				// if 1st character of name is a digit, its probably not a ref, it might be a literal? (+1, 1E3, etc)
				return null;
			} else if (parenCount == 0 && !Character.isAlphabetic(c)) {
				// not a character/digit, digit at beginning of name probably an expression not a ref
				return null;
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
	
	public static final String fixedWidthStr(String in, int width) {
		char[] chars = new char[width];
		for (int i=0; i < width; i++) {
			chars[i] = (in != null && i < in.length()) ? in.charAt(i) : ' ';
		}
		return new String(chars);
	}
	
	/** same as String.contains(char), but ignores stuff in quotes and parens */
	public static final boolean strContains(String str, char... toks) {
		// convert to set for quick lookup
		Set<Character> chars = new HashSet<>();
		for (char t : toks) chars.add(t);
		
		return strContains(str, chars);
	}
	
	public static final boolean strContains(String str, Set<Character> toks) {
		if (str == null || toks.isEmpty()) return false;
		
		boolean inquote = false;
		boolean inparen = false;
		int quotelevel = 0;
		for (int i=0; i < str.length(); i++) {
			char c = str.charAt(i);
			char n = (i+1 == str.length()) ? '\0' : str.charAt(i+1);
			
			if (c == '"') {
				if (inquote && n == '"') {
					// don't close the quote, its an escaped inner quote, consume one character and go on
					i++;
				} else {
					inquote = !inquote; // start/end of quote
				}
			} else if (!inquote && c == '(') {
				quotelevel++;
				inquote = true;
			} else if (!inquote && c == ')') {
				inquote = (--quotelevel > 0);
			} else if (!inquote && !inparen && toks.contains(c)) {
				return true;
			}
		}
		
		return false;
	}

	/** does this specified string match the specified pattern? */
	private static Pattern PAT1 = Pattern.compile("[0-9]+[ACELNPU]+");
	public static boolean matches(String str, String pat) {
		if (pat.equals("1L") && str.matches("[a-z]{1}")) {
			return true; // this is a horrible hack.
		}
		return false;
	}

	
}
