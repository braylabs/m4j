package com.braylabs.m4j.global;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.h2.mvstore.MVStore;

import com.braylabs.m4j.lang.M4JRuntime;
import com.braylabs.m4j.lang.M4JRuntime.M4JProcess;
import com.braylabs.m4j.parser.MParserUtils;

public class GOFImport {
	
	private static MVStore mvstore;
	
	public static void main(String[] args) throws Exception {
		int rowLimit = 100;
		File file = null;
		File store = null;
		for (int i=0; i < args.length; i++) {
			String arg = args[i];
			if ("-LIMIT".equalsIgnoreCase(arg)) {
				rowLimit = Integer.parseInt(args[++i]);
			} else if ("-STORE".equalsIgnoreCase(arg)) {
				store = new File(args[++i]);
			} else if (file == null) {
				File f = new File(args[i]);
				if (f.exists()) {
					file = f;
				}
			}
		}
		
		if (store != null && file != null) {
			mvstore = new MVStore.Builder().fileName(store.getAbsolutePath()).cacheSize(20).open();
			M4JRuntime runtime = new M4JRuntime(new GlobalStore.MVGlobalStore(mvstore));
			M4JProcess ctx = new M4JProcess(runtime, 0);
			loadFile(file, rowLimit, ctx);
//			MVStoreTool.dump(store.getAbsolutePath(), new PrintWriter(System.out));
		} else {
			System.err.println("Usage: GOFImport -store <FILE> -global <NAME> -limit <row limit> FILE");
		}
	}

	private static void loadFile(File f, int limit, M4JProcess ctx) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(f));
		
		String line = reader.readLine();
		List<String> toks = null;
		MVar map = null;
		int count = 0;
		while (line != null && count < limit) {
			if (!line.startsWith("^")) {
				line = reader.readLine();
				continue;
			}
			
			String gstr = MParserUtils.parseRoutineName(line.substring(1));
			if (map == null || !map.getName().equals(gstr)) {
				map = ctx.getGlobal(gstr);
			}
			if (line.indexOf('(') > 0 && line.endsWith(")")) {
				String args = line.substring(line.indexOf('(')+1,line.length()-1);
				toks = MParserUtils.tokenize(args, ',');
			}
			
			// now parse the value and set it
			String val = reader.readLine();
			System.out.println("Write: " + toks + ": " + val);
			map.get(toks.toArray(new String[0])).set(val);
			line = reader.readLine();
			count++;
		}
		mvstore.commit();
		System.out.println(map.get("loincdb","urn:lnc:100-8"));
		mvstore.close();
	}
}
