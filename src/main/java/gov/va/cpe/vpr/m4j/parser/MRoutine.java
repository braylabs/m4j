package gov.va.cpe.vpr.m4j.parser;


import gov.va.cpe.vpr.m4j.lang.M4JRuntime.M4JProcess;
import gov.va.cpe.vpr.m4j.lang.RoutineProxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class MRoutine extends AbstractMToken<MLine> implements RoutineProxy {
	public static String ROUTINE_HEADER_PATTERN = "^([a-zA-Z][\\w]*?)\\^INT\\^1\\^[\\w\\.\\,\\;]+\\^0.*$"; 

	protected Map<String,Integer> entryPointNames = new HashMap<String,Integer>();
	private List<String> content; // the original raw content
	private String name;
	
	public MRoutine(List<String> content) {
		super(content.get(0));
		this.name = MParserUtils.parseRoutineName(content.get(0));
		this.content = content;
		init();
	}
	
	protected void init() {
		this.children = new ArrayList<MLine>();
		
		// index the entry point locations in this routine
		for (int i=0; i < content.size(); i++) {
			children.add(i, null); // empty lines cache
			String name = MParserUtils.parseRoutineName(content.get(i));
			if (name != null) {
				entryPointNames.put(name, i);
			}
		}
	}
	
	@Override
	public Object call(String name, String entrypoint, M4JProcess proc, Object... params) {
		// get the lines to start evaluation at (evaluate
		Iterator<MLine> itr = getEntryPointLines(entrypoint);
		
		// run each line one at a time until we get a quit
		while (itr.hasNext()) {
			MLine line = itr.next();
			
			
			line.eval(proc);
		}
		
		// TODO: How to return?
		return null;
	}
	
	/**
	 * Returns a delayed evaluation iterator over each of the lines in the routine
	 */
	@Override
	public Iterator<MLine> iterator() {
		return iterator(0);
	}
	
	public Iterator<MLine> iterator(final int startAt) {
		final List<String> iterable = this.content;
		return new Iterator<MLine>() {
			int idx = startAt;
			
			@Override
			public boolean hasNext() {
				return (idx < iterable.size());
			}

			@Override
			public MLine next() {
				return getLine(idx++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}};
	}

	public String getName() {
		return name;
	}

	public int getLineCount() {
		return content.size();
	}
	
	public MLine getLine(int idx) {
		if (idx < 0 || (idx) >= children.size()) return null; // return null if out of bounds
		
		// if we have not already parsed the line, then parse it
		MLine line = children.get(idx);
		if (line == null) {
			String linestr = content.get(idx);
			children.set(idx, line = new MLine(linestr, idx));
		}
		
		return line; 
	}
	
	public boolean hasEntryPoint(String name) {
		return entryPointNames.containsKey(name);
	}
	
	public Set<String> getEntryPointNames() {
		return entryPointNames.keySet();
	}
	
	public Iterator<MLine> getEntryPointLines(String entryPoint) {
		if (entryPoint == null) return iterator(); // null== start a the top
		
		// throw an error if it doesn't exist
		if (!entryPointNames.containsKey(entryPoint)) {
			throw new IllegalArgumentException("Entrypoint: " + entryPoint + " does not exist in routine: " + getName());
		}
		return iterator(entryPointNames.get(entryPoint));
	}
	
	/**
	 * Parses a single routine from a .int file.
	 */
	public static MRoutine parseFromFile(File file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(file));
		
		// parse the first line to get the routine name
		String line = reader.readLine();
		String name = MParserUtils.parseRoutineName(line);
		if (name == null) return null;
		
		List<String> lines = new ArrayList<String>();
		lines.add(line);
		while (line != null) {
			lines.add(line);
			line = reader.readLine();
		}
		
		reader.close();
		return new MRoutine(lines);
	}
	
	/**
	 * Parse all the routines in a .ro-formatted file/stream
	 */
	public static List<MRoutine> parseRoutineOutputFile(InputStream is) throws IOException {
		List<String> buff = null;
		List<MRoutine> ret = new ArrayList<MRoutine>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		
		// Read the file line-by-line
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.matches(ROUTINE_HEADER_PATTERN)) {
				if (buff != null) {
					ret.add(new MRoutine(buff));
				} 
				buff = new ArrayList<String>();
			} else if (buff != null) {
				buff.add(line);
			}
		}
		
		// if the buffer is not empty, we have our final routine
		if (buff != null) {
			ret.add(new MRoutine(buff));
		}
		
		reader.close();
		return ret;
	}
}