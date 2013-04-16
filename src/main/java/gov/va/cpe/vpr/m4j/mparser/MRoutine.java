package gov.va.cpe.vpr.m4j.mparser;


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

public class MRoutine extends MToken<MLine> {
	private static Pattern ROUTINE_HEADER_PATTERN = Pattern.compile("^([a-zA-Z][\\w]*?)\\^INT\\^1\\^[\\d]+,[\\d]+\\.[\\d]+\\^0.*$"); 

	private Map<String,Integer> entryPointNames = new HashMap<String,Integer>();
	private Map<Integer,String> entryPointLines = new HashMap<Integer,String>(); 
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
		
		// index the entrypoints in this routine
		for (int i=0; i < content.size(); i++) {
			children.add(i, null); // empty lines cache
			String name = MParserUtils.parseRoutineName(content.get(i));
			if (name != null) {
				entryPointNames.put(name, i);
				entryPointLines.put(i, name);
			}
		}
	}
	
	/**
	 * Returns a delayed evaluation iterator over each of the lines in the routine
	 */
	@Override
	public Iterator<MLine> iterator() {
		final List<String> iterable = this.content;
		return new Iterator<MLine>() {
			int idx = 0;
			
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
	
	public List<MLine> getEntryPointLines() {
		return getEntryPointLines(null);
	}
	
	public List<MLine> getEntryPointLines(String entryPoint) {
		// use the routine name as the default
		if (entryPoint == null) entryPoint = getName();
		
		// throw an error if it doesn't exist
		if (!entryPointNames.containsKey(entryPoint)) {
			throw new IllegalArgumentException("Entrypoint: " + entryPoint + " does not exist in routine: " + getName());
		}
		
		// collect all the lines that belong to the entry point
		List<MLine> ret = new ArrayList<MLine>();
		int i=entryPointNames.get(entryPoint);
		do {
			ret.add(getLine(i++));
		} while(i < children.size() && !entryPointLines.containsKey(i));
		return ret;
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
			if (line.matches(ROUTINE_HEADER_PATTERN.pattern())) {
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