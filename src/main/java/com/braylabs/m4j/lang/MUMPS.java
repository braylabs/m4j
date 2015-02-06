package com.braylabs.m4j.lang;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.global.MVar.MVarKey;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JEntryPoint;
import com.braylabs.m4j.lang.RoutineProxy.JavaClassProxy.M4JRoutine;
import com.braylabs.m4j.parser.MParserUtils;

@M4JRoutine(name="SYS")
public class MUMPS {
	
	public static final Set<String> FUNCTION_SET = Collections
			.unmodifiableSet(new HashSet<String>(Arrays.asList(
					"$A", "$ASCII", "$C", "$CHAR", "$EF", "$EXTRACT", "$F", "$FIND", "$P", "$PIECE", "$R", "$RANDOM", "$RE", "$REVERSE",
					"$D", "$G", "$I", "$NA", "$O", "$QL", "$QS", "$Q", 
					"$FN", "$J", "$L", "$TR", "$S", "$ST", "$T", "$V")));
	
	private static Random rand = new Random();

	/* TODO: Other ANSI-standard M functions to implement
	 * GLobals (these need MMap to be finished first)
	$Increment()
	$NAme()
	$Next()
	$Qlength()
	$QSubscript()
	$Query()
	
	Other string functions:
	$FNumber()
	$Justify()
	$Length()
	$Translate()
	
	Not sure if these can be done yet:
	$Select()
	$STack()
	$Text()
	$View()
	*/
	
	/**
	 * Returns the MUMPS style date of "ddddd,sssss" which represents 
	 * the number of days since December 31, 1840, where day 1 is January 1, 1841 and sssss represents 
	 * the number of seconds since midnight of the current day 
	 * 
	 * TODO: implement this
	 * 
	 * @return
	 */
	public static final MVar $HOROLOG() {
		return new MVar.TreeMVar("$HOROLOG", "63568,56134");
	}

	@M4JEntryPoint(name={"$L","$LENGTH"})
	public static final int $L(String variable) {
		return $L(variable, null);
	}

	@M4JEntryPoint(name={"$L","$LENGTH"})
	public static final int $L(String str, Object delim) {
		if (str == null) return 0;
		
		// evaluate as string
		return str.length();

		// TODO: Finish the delimiter stuff
	}
	
	// $TRANSLATE function ----------------------------------------------------
	
	@M4JEntryPoint(name={"$TR","$TRANSLATE"})
	public static final String $TRANSLATE(String string, String identifier) {
		return $TRANSLATE(string, identifier, "");
	}
	
	/** Performs character-for-character replacement within a string. */
	@M4JEntryPoint(name={"$TR","$TRANSLATE"})
	public static final String $TRANSLATE(String string, String identifier, String assoc) {
		StringBuilder sb = new StringBuilder(string.toString());
		
		// loop through each character in the identifier string
		for (int i=0; i < sb.length(); i++) {
			char c = sb.charAt(i);
			
			// if c is not found in the target list, continue to next character
			int idx = identifier.indexOf(c);
			if (idx == -1) continue;
			
			// not enough replacement characters, remove the match
			if (idx >= assoc.length()) {
				sb.replace(i, i+1, "");
				i--;
			} else {
				sb.replace(i, i+1, "" + assoc.charAt(idx));
			}
		}
		
		return sb.toString();
	}
	
	// $INCREMENT function ----------------------------------------------------

	public static final Number $I(MVar variable) {
		return $INCREMENT(variable, null);
	}

	public static final Number $I(MVar variable, Object num) {
		return $INCREMENT(variable, num);
	}

	
	public static final Number $INCREMENT(MVar variable) {
		return $INCREMENT(variable, null);
	}
	
	/**
	 * TODO: Make this an atomic increment by using a lock on the variable?
	 * TODO: is this properly preserving type (integer, double, string, etc.) should it?
	 */
	public static final Number $INCREMENT(MVar variable, Object num) {
		Number val = (variable.isDefined()) ? MParserUtils.evalNumericValue(variable.val()) : 0;
		Number inc = (num == null) ? 1 : MParserUtils.evalNumericValue(num);
		
		Number ret = null;
		double d = val.doubleValue() + inc.doubleValue();
		int i = val.intValue() + inc.intValue();
		if (i == d) {
			ret = new Integer(i);
		} else {
			ret = new Double(d);
		}
		
		// update the variable and return
		variable.set(ret);
		
		return ret;
	}
	
	// $DATA function ---------------------------------------------------------
	
	@M4JEntryPoint(name={"$D","$DATA"})
	public static final int $DATA(MVar variable) {
		return $DATA(variable, null);
	}

	@M4JEntryPoint(name={"$D","$DATA"})
	public static final int $DATA(MVar variable, MVar target) {
		boolean hasData = variable.isDefined();
		boolean hasDesc = variable.hasDescendents();
		
		int ret = 0;
		if (hasData && hasDesc) ret=11;
		else if (!hasData && hasDesc) ret=10;
		else if (hasData) ret=1;
		
		if (target != null) {
			target.set(ret);
		}
		return ret;
	}
	
	// $GET function ----------------------------------------------------------

	@M4JEntryPoint(name={"$G","$GET"})
	public static final Object $G(MVar variable) {
		return $GET(variable, null);
	}

	public static final Object $G(MVar variable, Object defaultVal) {
		return $GET(variable, defaultVal);
	}
	
	public static final Object $G(MVar variable, MVar defaultVal) {
		return $GET(variable, defaultVal);
	}

	@M4JEntryPoint(name={"$G","$GET"})
	public static final Object $GET(MVar variable, Object defaultVal) {
		if (variable.isDefined()) return variable.val();
		return defaultVal;
	}

	@M4JEntryPoint(name={"$G","$GET"})
	public static final Object $GET(MVar variable, MVar defaultVal) {
		if (variable.isDefined()) return variable.val();

		if (defaultVal != null && !defaultVal.isDefined()) throw new UndefinedVariableException(defaultVal); 
		if (defaultVal != null) return defaultVal.val();
		
		return "";
	}
	
	// $ORDER function --------------------------------------------------------

	@M4JEntryPoint(name={"$O","$ORDER"})
	public static final Object $ORDER(MVar variable) {
		return $ORDER(variable, 1, null);
	}

	@M4JEntryPoint(name={"$O","$ORDER"})
	public static final Object $ORDER(MVar variable, int direction) {
		return $ORDER(variable, direction, null);
	}
	
	@M4JEntryPoint(name={"$O","$ORDER"})
	public static final Object $ORDER(MVar variable, int direction, MVar target) {
		MVarKey ret = null;
		if (direction == 1) {
			ret = variable.nextKey();
		} else if (direction == -1) {
			ret = variable.prevKey();
		} else {
			throw new IllegalArgumentException("Only 1 and -1 are allowed for direction value");
		}
		
		if (target != null && ret != null) target.set(ret.getLastKey());
		if (ret == null) return null;
		return ret.getLastKey();
	}
	
	
	// $ASCII function --------------------------------------------------------
	
	public static final int $A(String str) {
		return $ASCII(str, 1);
	}
	
	public static final int $A(String str, int idx) {
		return $ASCII(str, idx);
	}
	
	public static final int $ASCII(String str) {
		return $ASCII(str, 1);
	}
	
	public static final int $ASCII(String str, int idx) {
		if (str == null || idx > str.length() || idx <= 0) return -1;
		return str.codePointAt(idx-1);
	}
	
	// $CHAR function
	
	public static final String $C(int... chars) {
		return $CHAR(chars);
	}
	
	public static final String $CHAR(int... chars) {
		if (chars.length == 0) throw new IllegalArgumentException("You must specify at least 1 value to $CHAR()");
		String ret = "";
		for (int c : chars) {
			ret += (char) c;
		}
		return ret;
	}
	
	// $EXTRACT function
	
	public static final String $E(String str) {
		return $EXTRACT(str, 1, 1);
	}

	public static final String $E(String str, int start) {
		return $EXTRACT(str, start, start);
	}

	public static final String $E(String str, int start, int end) {
		return $EXTRACT(str, start, end);
	}
	
	public static final String $EXTRACT(String str) {
		return $EXTRACT(str, 1, 1);
	}

	public static final String $EXTRACT(String str, int start) {
		return $EXTRACT(str, start, start);
	}

	public static final String $EXTRACT(String str, int start, int end) {
		if (str == null || start <= 0 || end < start) return "";
		return str.substring(start-1, (end >= str.length()) ? str.length() : end);
	}
	
	// $FIND function ---------------------------------------------------------
	
	public static final int $F(String str, String substr) {
		return $FIND(str, substr, 1);
	}
	
	public static final int $F(String str, String substr, int start) {
		return $FIND(str, substr, start);
	}
	
	public static final int $FIND(String str, String substr) {
		return $FIND(str, substr, 1);
	}
	
	public static final int $FIND(String str, String substr, int start) {
		if (str == null || substr == null) return 0;
		int ret = str.indexOf(substr, start-1); 
		return (ret == -1) ? 0 : ret + substr.length() + 1;
	}
	
	// $PIECE function --------------------------------------------------------
	@M4JEntryPoint(name={"$P","$PIECE"})
	public static final String $PIECE(String str, String delim) {
		return $PIECE(str, delim, 1, 1);
	}

	@M4JEntryPoint(name={"$P","$PIECE"})
	public static final String $PIECE(String str, String delim, int first) {
		return $PIECE(str, delim, first, first);
	}

	@M4JEntryPoint(name={"$P","$PIECE"})
	public static final String $PIECE(String str, String delim, int first, int last) {
		StringTokenizer st = new StringTokenizer(str, delim);
		String ret = "";
		int count = 0;
		while (st.hasMoreTokens()) {
			count++;
			if (count > last) break;
			String token = st.nextToken();
			
			if (count >= first) {
				ret += token;
			}
			if (count >= first && count < last && last > first && st.hasMoreTokens()) {
				ret += delim;
			}
		}
		return ret;
	}
	
	// $Random function ------------------------------------------------------- 
	public static final int $R(int limit) {
		return $RANDOM(limit);
	}
	
	public static final int $RANDOM(int limit) {
		if (limit <= 0) throw new IllegalArgumentException("Limit cannot be <= 0 in $R(...)");
		return rand.nextInt(limit);
	}
	
	// $REverse function ------------------------------------------------------
	
	
	@M4JEntryPoint(name={"$R","$REVERSE"})
	public static final int $REVERSE(int val) {
		return Integer.parseInt($REVERSE(new Integer(val).toString()));	
	}
	
	@M4JEntryPoint(name={"$R","$REVERSE"})
	public static final String $REVERSE(String str) {
		if (str == null) return "";
		return new StringBuffer(str).reverse().toString();
	}
	
	// Exceptions -------------------------------------------------------------
	
	public static class UndefinedVariableException extends RuntimeException {
		public UndefinedVariableException(MVar var) {
			super("<UNDEFINED> *" + var.getFullName());
		}
	}
	
}
