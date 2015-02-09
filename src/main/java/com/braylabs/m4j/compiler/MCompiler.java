package com.braylabs.m4j.compiler;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.braylabs.m4j.parser.MInterpreter;
import com.braylabs.m4j.parser.MUMPSBaseVisitor;
import com.braylabs.m4j.parser.MUMPSParser;
import com.braylabs.m4j.parser.MUMPSParser.EpArgsContext;
import com.braylabs.m4j.parser.MUMPSParser.FileContext;
import com.braylabs.m4j.parser.MUMPSParser.RoutineLineContext;

public class MCompiler extends MUMPSBaseVisitor<Object> {
	
	public static final FileFilter M_FILTER = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory() || (pathname.isFile() && pathname.getName().endsWith(".int"));
		}
	};
	
	public static final FilenameFilter M_FILTER2 = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			File f = new File(dir, name);
			if (f.isDirectory()) return true;
			return name.endsWith(".int");
		}
	};
	
	public static String pkgname="";
	public static void main(String[] args) throws FileNotFoundException, IOException {
		File base = new File(".");
		
		// process args
		List<File> files = new ArrayList<>();
		for (String str : args) {
			if (str.startsWith("-package=")) {
				pkgname = str.split("=")[1];
			} else if (str.startsWith("-dest=")) {
				base = new File(str.split("=")[1]);
			} else {
				files.add(new File(str));
			}
		}
		base.mkdirs();
		System.out.println("Pkg Prefix: " + pkgname);
		System.out.println("Dest Dir: " + base);
		
		for (int i=0; i < files.size(); i++) {
			File f = files.get(i);
			if (f.isDirectory() && f.exists()) {
				files.addAll(Arrays.asList(f.listFiles(M_FILTER)));
			} else if (f.exists() && f.isFile()) {
				System.out.println("Compiling: " + f);
				File target = new File(base, f.getName().replace(".int", ".class"));
				FileOutputStream fos = new FileOutputStream(target);
				MUMPSParser parser = MInterpreter.parse(new FileReader(f));
				fos.write(compile(parser, f));
				fos.close();
			}
		}
	}
	
	public static byte[] compile(MUMPSParser parser, File f) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES+ClassWriter.COMPUTE_MAXS);
		FileContext file = parser.file();
		String name = file.routineLine(0).entryPoint().ID().getText();	
		
		cw.visitSource(f.toString(), null);
		
		// first write the class structure
		cw.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, pkgname + name, null, "java/lang/Object", null);
		
		// then write each entrypoint as a method
		for (RoutineLineContext line : file.routineLine()) {
			if (line.entryPoint() != null) {
				String epName = line.entryPoint().ID().getText();
				
				// loop through args, treat them all as objects for now
				Type[] argTypes = new Type[] {};
				EpArgsContext args = line.entryPoint().epArgs();
				if (args != null) {
					argTypes = new Type[args.ID().size()];
					for (int i=0; i < argTypes.length; i++) {
						String argName = args.ID(i).getText();
						argTypes[i] = Type.getType(Object.class);
					}
				}
				
				
				String type = Type.getMethodDescriptor(Type.getType(Void.TYPE), argTypes);
				cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, epName, type, null, null);
			}
		}
		
		cw.visitEnd();
		return cw.toByteArray();
	}
}
