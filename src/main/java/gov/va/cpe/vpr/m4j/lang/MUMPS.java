package gov.va.cpe.vpr.m4j.lang;

import gov.va.cpe.vpr.m4j.global.MMap;
import gov.va.cpe.vpr.m4j.global.MVar;
import gov.va.cpe.vpr.m4j.global.MVar.MVarKey;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

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
	
	// $DATA function ---------------------------------------------------------
	
	public static final int $D(MVar variable) {
		return $DATA(variable, null);
	}
	
	public static final int $D(MVar variable, MVar target) {
		return $DATA(variable, target);
	}

	public static final int $DATA(MVar variable) {
		return $DATA(variable, null);
	}

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

	public static final Object $G(MVar variable) {
		return $GET(variable, null);
	}
	
	public static final Object $G(MVar variable, Object defaultVal) {
		return $GET(variable, defaultVal);
	}
	
	public static final Object $G(MVar variable, MVar defaultVal) {
		return $GET(variable, defaultVal);
	}

	public static final Object $GET(MVar variable, Object defaultVal) {
		if (variable.isDefined()) return variable.val();
		return defaultVal;
	}
	
	public static final Object $GET(MVar variable, MVar defaultVal) {
		if (variable.isDefined()) return variable.val();

		if (defaultVal != null && !defaultVal.isDefined()) throw new UndefinedVariableException(defaultVal); 
		if (defaultVal != null) return defaultVal.val();
		
		return "";
	}
	
	// $ORDER function --------------------------------------------------------

	public static final Object $O(MVar variable) {
		return $ORDER(variable, 1, null);
	}
	
	public static final Object $O(MVar variable, int direction) {
		return $ORDER(variable, direction, null);
	}
	
	public static final Object $O(MVar variable, int direction, MVar target) {
		return $ORDER(variable, direction, target);
	}
	
	public static final Object $ORDER(MVar variable, int direction) {
		return $ORDER(variable, direction, null);
	}
	
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
	public static final String $P(String str, String delim) {
		return $PIECE(str, delim, 1, 1);
	}

	public static final String $P(String str, String delim, int first) {
		return $PIECE(str, delim, first, first);
	}

	public static final String $P(String str, String delim, int first, int last) {
		return $PIECE(str, delim, first, last);
	}
	
	public static final String $PIECE(String str, String delim) {
		return $PIECE(str, delim, 1, 1);
	}

	public static final String $PIECE(String str, String delim, int first) {
		return $PIECE(str, delim, first, first);
	}

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
	
	public static final int $RE(int val) {
		return $REVERSE(val);
	}
	
	public static final String $RE(String str) {
		return $REVERSE(str);
	}
	
	public static final int $REVERSE(int val) {
		return Integer.parseInt($REVERSE(new Integer(val).toString()));	
	}
	
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
