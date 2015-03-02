package com.braylabs.m4j.lang;

import java.util.HashMap;
import java.util.Map;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.parser.MParserUtils;

/** Intended to represent a M Value which can be evaluated in a variety of ways implement operators as well */
public class MVal {
	
	public static Map<String,BinaryOp> BINARY_OPS = new HashMap<>();
	public static Map<String,UnaryOp> UNARY_OPS = new HashMap<>();
	public static MVal TRUE = new MVal(1);
	public static MVal FALSE = new MVal(0);
	private Number numVal;
	private String strVal;
	private Object objVal;

	static {
		// index the operators by their symbol
		for (BinaryOp op : BinaryOp.values()) {
			BINARY_OPS.put(op.toString(), op);
		}
		
		for (UnaryOp op : UnaryOp.values()) {
			UNARY_OPS.put(op.toString(), op);
		}
	}

	public MVal(Object obj) {
		if (obj == null) obj = "";
		objVal = obj;
		numVal = MParserUtils.evalNumericValue(obj);
		strVal = obj.toString();
	}
	
	public MVal apply(UnaryOp op) {
		switch(op) {
			// string to number
			case POS: return new MVal(this.numVal);
			case NEG: return new MVal(this.numVal.intValue()*-1);
			case NOT: return new MVal((this.numVal.equals(0)) ? 1 : 0);
			default:
				throw new RuntimeException("Operator not implemented: "  + op);
		}
	}
	
	public MVal apply(BinaryOp op, MVal val) {
		Double numval = 0d, n1 = this.numVal.doubleValue(), n2 = val.numVal.doubleValue();
		String strval = null;
		
		switch (op) {
			// Arithmetic operators
			case ADD: numval = n1 + n2; break;
			case SUB: numval = n1 - n2; break;
			case MULT: numval = n1 * n2; break;
			case DIV: numval = n1 / n2; break;
			case INT_DIV: numval = Math.floor(n1 / n2); break;
			case MOD: numval = n1 % n2; break;
			// TODO: 0**$DOUBLE(0) is 1 not 0 (the regular Math.pow() response)
			case EXP: numval = (n1==0 && n2 == 0) ? 0 : Math.pow(n1, n2); break;
			
			// logical comparison
			case GT: numval = (n1 > n2) ? 1d : 0d; break;
			case GTE: 
			case NLT: numval = (n1 >= n2) ? 1d : 0d; break;
			case LT: numval = (n1 < n2) ? 1d : 0d; break;
			case LTE: 
			case NGT: numval = (n1 <= n2) ? 1d : 0d; break;
			
			// If the two operands are of different types, both operands are converted to strings 
			// and those strings are compared.
			case EQ: numval = (this.equals(val)) ? 1d : 0d; break;
			case NEQ: numval = (!n1.equals(n2)) ? 1d : 0d; break;

			// string operators
			case CONCAT: strval = this.strVal + val.strVal; break; 
			case CONTAINS: numval = (this.strVal.contains(val.strVal)) ? 1d : 0d; break;
			case NOT_CONTAINS: numval = (!this.strVal.contains(val.strVal)) ? 1d : 0d; break;
			
			case MATCH: numval = matches(val) ? 1d : 0d; break;
			case NOT_MATCH: numval = !matches(val) ? 1d : 0d; break;
			
			default:
				throw new RuntimeException("Operator not implemented: "  + op);
			
		}
		
		// if string return value
		if (strval != null) return new MVal(strval);
		
		// if floating point but with no decimal, return integer
		if (numval % 1 == 0) {
			return new MVal(numval.intValue());
		} else {
			return new MVal(numval);
		}
	}
	
	public boolean matches(MVal val) {
		return MParserUtils.matches(this.toString(), val.toString());
	}
	
	public boolean matches(int offset, int repeat, char... codes) {
		MatchOp[] ops = new MatchOp[codes.length];
		for (int i=0; i < codes.length; i++) {
			ops[i] = MatchOp.valueOf("" + codes[i]);
		}
		return matches(offset, repeat, ops);
	}
	
	public boolean matches(int offset, int repeat, MatchOp... codes) {
		// validate
		if (offset < 0 || repeat == 0) return false;
		
		for (int i=0; i < repeat; i++) {
			if (offset + i >= strVal.length()) return false;
			char c = strVal.charAt(offset + i);
			
			boolean val = false;
			for (int j=0; j < codes.length; j++) {
				MatchOp code = codes[j];
				if (code == MatchOp.A && Character.isAlphabetic(c)) {
					val = true; break; // alpha
				} else if (code == MatchOp.C && ((c >= 0 && c <= 31) || (c >= 127 && c <= 159))) {
					val = true; break; // control character
				} else if (code == MatchOp.L && Character.isLowerCase(c)) {
					val = true; break; // lowercase
				} else if (code == MatchOp.U && Character.isUpperCase(c)) {
					val = true; break; // uppercase
				} else if (code == MatchOp.N && Character.isDigit(c)) {
					val = true; break; // numeric digit
				} else if (code == MatchOp.E) {
					val = true; break; // any characters
				} else if (code == MatchOp.P && (
						(c >= 32 && c <= 47) || (c >= 58 && c <= 64) ||
						(c >= 91 && c <= 96) || (c >= 123 && c <= 126) ||
						(c >= 160 && c <= 169) || (c >= 171 && c <= 177) ||
						(c >= 182 && c <= 184) || c == 180 || c == 187 || 
						c == 191 || c == 215 || c == 247)) {
					val = true; break; // punctuation
				}
			}
			
			if (!val) return false;
		}
		
		return true;
	}
	
	public boolean matches(int offset, int repeat, String literal) {
		int idx = 0;

		// bad arguments
		if (repeat == 0) return false;
		if (offset < 0) return false;
		
		// empty string only matches another empty string, otherwise nothing
		if (literal.isEmpty() && strVal.isEmpty()) return true;
		if (literal.isEmpty()) return false;

		if (repeat < 0) repeat = (strVal.length()-offset) / literal.length();
		for (int i=0; i < repeat; i++) {
			if (offset + idx > strVal.length()) break;
			String substr = strVal.substring(offset + idx);
			if (!substr.startsWith(literal)) {
				return false;
			}
			idx += literal.length();
		}
		
		return true;
	}
	
	@Override
	public boolean equals(Object obj) {
		return this.strVal.equals(obj.toString());
	}
	
	@Override
	public String toString() {
		return strVal;
	}
	
	public Number toNumber() {
		return numVal;
	}
	
	public Object getOrigVal() {
		return objVal;
	}
	
	public boolean isTruthy() {
		return !numVal.equals(0);
	}
	
	public static final MVal valueOf(Object obj) {
		if (obj instanceof MVal) return (MVal) obj;
		if (obj instanceof MVar) { 
			MVar var = (MVar) obj;
			if (!var.isDefined()) throw new IllegalArgumentException("<UNDEFINED> " + obj.toString());
			return new MVal(var.val());
		} else if (obj instanceof Boolean) {
			return (((Boolean) obj).booleanValue()) ? MVal.TRUE : MVal.FALSE;
		}
		return new MVal(obj);
	}
	
	public enum BinaryOp {
		ADD("+"),SUB("-"),MULT("*"),DIV("/"),INT_DIV("\\"),EXP("**"),MOD("#"), // arithmetic
		GT(">"),GTE(">="),NLT("'<"),LT("<"),LTE("<="),NGT("'>"), EQ("="), NEQ("'="), // logical comparison
		CONCAT("_"),CONTAINS("["),NOT_CONTAINS("'["), MATCH("?"), NOT_MATCH("'?"), // string
		FOLLOWS("]"), NOT_FOLLOWS("']"), SORT_AFTER("]]"), NOT_SORT_AFTER("']]"), // string
		LP("(",4), RP(")",4); // non-associative operators

		private String symbol;
		private int type=2; // 1=unary,2=binary,3=ambiguous,4=other
		
		BinaryOp(String symbol) {
			this.symbol = symbol;
		}
		
		BinaryOp(String symbol, int type) {
			this.symbol = symbol;
			this.type = type;
		}
		
		@Override
		public String toString() {
			return this.symbol;
		}
		
	}
	
	public enum UnaryOp {
		POS("+"), NEG("-"), NOT("'");
		
		private String symbol;
		UnaryOp(String symbol) {
			this.symbol = symbol;
		}
		
		@Override
		public String toString() {
			return this.symbol;
		}
	}
	
	public enum MatchOp {
		/** A = Alphabetic characters */
		A,
		/** C = Control characters */
		C,
		/** E = Any character (including whitespace and control */
		E,
		/** L = Any lower case alphabetic character */
		L,
		/** N = Any numerical digit 0-9 */
		N,
		/** P = Any punctuation character */
		P,
		/** U = Any upper case alphabetic character */
		U;
	}
}
