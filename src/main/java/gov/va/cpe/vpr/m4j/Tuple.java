package gov.va.cpe.vpr.m4j;

import java.io.Serializable;
import java.util.Arrays;

public class Tuple<K extends Serializable & Comparable<K>> implements Comparable<K>, Serializable{
	private static final long serialVersionUID = -2348889938449265179L;
		private K[] vals;
        public Tuple(K... vals) {
        	this.vals = vals;
        }

        public int compareTo(K o) {
            final Tuple<?> t = (Tuple<?>) o;
            if (this.vals.length != t.vals.length) {
            	return ((Integer) this.vals.length).compareTo(t.vals.length);
            }
            
            for (int i=0; i < this.vals.length; i++) {
            	K k1 = this.vals[i], k2 = (K) t.vals[i];
            	
        		final int c = k1.compareTo(k2);
        		if (k1 == k2 || c !=0) return c;
            }

            return 0;
        }


        @Override public String toString() {
            return Arrays.toString(this.vals);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            // check that both tupes are of the same length
            Tuple<?> tuple = (Tuple<?>) o;
            if (tuple.vals.length != vals.length) return false;
            
            // check each value
            for (int i=0; i < this.vals.length; i++) {
            	K val = this.vals[i];
            	if ((val != null) ? val.equals(tuple.vals[i]) : tuple.vals[i] != null) return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = 0;
            for (int i=0; i < this.vals.length; i++) {
            	result = ((i==0) ? 1 : 31) * (result + ((this.vals[i] == null) ? 0 : this.vals[i].hashCode()));
            }
            return result;
        }
}
