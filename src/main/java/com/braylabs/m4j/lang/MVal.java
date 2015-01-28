package com.braylabs.m4j.lang;

import java.util.HashMap;
import java.util.Map;

import com.braylabs.m4j.global.MVar;
import com.braylabs.m4j.parser.MParserUtils;

/** Intended to represent a M Value which can be evaluated in a variety of ways implmenent operators as well */
public class MVal {
	
	Number numVal;
	String strVal;
	
	public MVal(Object obj) {
		if (obj == null) obj = "";
		numVal = MParserUtils.evalNumericValue(obj);
		strVal = obj.toString();
	}
	
	public MVal apply(UnaryOp op) {
		switch(op) {
			// string to number
			case POS: return new MVal(MParserUtils.evalNumericValue(this.strVal));
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
			case GTE: numval = (n1 >= n2) ? 1d : 0d; break;
			case LT: numval = (n1 < n2) ? 1d : 0d; break;
			case LTE: numval = (n1 <= n2) ? 1d : 0d; break;
			
			// If the two operands are of different types, both operands are converted to strings 
			// and those strings are compared.
			case EQ: numval = (this.equals(val)) ? 1d : 0d; break;
			case NEQ: numval = (!n1.equals(n2)) ? 1d : 0d; break;

			// string operators
			case CONCAT: strval = this.strVal + val.strVal; break; 
			case CONTAINS: numval = (this.strVal.contains(val.strVal)) ? 1d : 0d; break;
			case NOT_CONTAINS: numval = (!this.strVal.contains(val.strVal)) ? 1d : 0d; break;
			
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
	
	public static final MVal valueOf(Object obj) {
		if (obj instanceof MVal) return (MVal) obj;
		if (obj instanceof MVar) { 
			MVar var = (MVar) obj;
			if (!var.isDefined()) throw new IllegalArgumentException("Variable: " + obj.toString() + " is referenced but undefined when its value is needed");
			return new MVal(var.val());
		}
		return new MVal(obj);
	}
	
	public enum BinaryOp {
		ADD("+"),SUB("-"),MULT("*"),DIV("/"),INT_DIV("\\"),EXP("**"),MOD("#"), // arithmetic
		GT(">"),GTE(">=","'<"),LT("<"),LTE("<=","'>"), EQ("="), NEQ("'="), // logical comparison
		CONCAT("_"),CONTAINS("["),NOT_CONTAINS("'["), MATCH("?"), NOT_MATCH("'?"), // string
		FOLLOWS("]"), NOT_FOLLOWS("']"), SORT_AFTER("]]"), NOT_SORT_AFTER("']]"); // string

		private String[] symbols;
		BinaryOp(String... symbols) {
			this.symbols = symbols;
		}
		
		@Override
		public String toString() {
			return this.symbols[0];
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
	
	public static Map<String,BinaryOp> BINARY_OPS = new HashMap<>();
	public static Map<String,UnaryOp> UNARY_OPS = new HashMap<>();
	static {
		// index the operators by their symbol
		for (BinaryOp op : BinaryOp.values()) {
			for (String symbol : op.symbols) {
				BINARY_OPS.put(symbol, op);
			}
		}
		
		for (UnaryOp op : UnaryOp.values()) {
			UNARY_OPS.put(op.toString(), op);
		}
	}

	

}
