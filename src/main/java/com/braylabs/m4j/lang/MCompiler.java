package com.braylabs.m4j.lang;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.TraceClassVisitor;

import com.braylabs.m4j.parser.MUMPSBaseVisitor;
import com.braylabs.m4j.parser.MUMPSLexer;
import com.braylabs.m4j.parser.MUMPSParser;
import com.braylabs.m4j.parser.MInterpreter.UnderlineErrorListener;
import com.braylabs.m4j.parser.MUMPSParser.CmdContext;
import com.braylabs.m4j.parser.MUMPSParser.CmdListContext;
import com.braylabs.m4j.parser.MUMPSParser.EpArgsContext;
import com.braylabs.m4j.parser.MUMPSParser.ExprContext;
import com.braylabs.m4j.parser.MUMPSParser.FileContext;
import com.braylabs.m4j.parser.MUMPSParser.RefContext;
import com.braylabs.m4j.parser.MUMPSParser.RoutineLineContext;

public class MCompiler extends MUMPSBaseVisitor<Object> {
	
	public static final FileFilter M_FILTER = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory() || (pathname.isFile() && pathname.getName().endsWith(".int"));
		}
	};
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		String pkgname="";
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
				
				MCompiler comp = new MCompiler(pkgname, f);
				fos.write(comp.compile());
				fos.close();
			}
		}
	}
	
	public static CommonTokenStream getTokenStream(Reader reader) throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(reader);
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return tokens;
	}

	private MUMPSParser parser;
	private File file;
	private String pkg;
	private MethodVisitor curMethod;
	private Map<String,Integer> curSymbols;
	
	public MCompiler(String pkgName, File f) throws FileNotFoundException, IOException {
		this.file = f;
		this.pkg = pkgName;
		
		// setup the parser with empty string for now
		ANTLRInputStream input = new ANTLRInputStream(new FileReader(f));
		MUMPSLexer lexer = new MUMPSLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		this.parser = new MUMPSParser(tokens);
		
		// setup error handler on the parser
		this.parser.removeErrorListeners();
		this.parser.addErrorListener(new UnderlineErrorListener());
	}
	
	public byte[] compile() {
		PrintWriter pw = new PrintWriter(System.out);
		ClassWriter cw0 = new ClassWriter(ClassWriter.COMPUTE_FRAMES+ClassWriter.COMPUTE_MAXS);
		TraceClassVisitor cw = new TraceClassVisitor(cw0,pw);
		FileContext file = parser.file();
		String name = file.routineLine(0).entryPoint().ID().getText();	
		cw.visitSource(this.file.toString(), null);
		
		// first write the class structure
		cw.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, this.pkg.replace('.','/') + name, null, "java/lang/Object", null);
		
		// then write each entrypoint as a method
		for (RoutineLineContext line : file.routineLine()) {
			if (line.entryPoint() != null) {
				String epName = line.entryPoint().ID().getText();
				
				// if the name is the same as the class name, skip it for now
				if (epName.equals(name)) continue;
				
				// loop through args, treat them all as objects for now
				Map<String,Integer> argsMap = new HashMap<>();
				Type[] argTypes = new Type[] {};
				EpArgsContext args = line.entryPoint().epArgs();
				if (args != null) {
					argTypes = new Type[args.ID().size()];
					for (int i=0; i < argTypes.length; i++) {
						String argName = args.ID(i).getText();
						argTypes[i] = Type.getType(String.class);
						argsMap.put(argName, i+1);
					}
				}
				curSymbols = argsMap;
				
				
				String type = Type.getMethodDescriptor(Type.getType(Void.TYPE), argTypes);
				curMethod = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, epName, type, null, null);
				
				curMethod.visitCode();
				if (line.cmdList() != null) {
					visitCmdList(line.cmdList());
				}
				
				curMethod.visitEnd();
			}
		}
		
		cw.visitEnd();
		return cw0.toByteArray();
	}
	
	@Override
	public Object visitCmdList(CmdListContext ctx) {
		for (CmdContext cmd : ctx.cmd()) {
			visit(cmd);
		}
		return null;
	}
	
	@Override
	public Object visitCmd(CmdContext ctx) {
		// ensure that this command is defined/implemented
		String name = ctx.ID().getText();
		
		// TODO: If there is a pce, evaluate it and jump somewhere otherwise?
		
		switch (name.toUpperCase()) {
			case "W":
			case "WRITE":
				return CMD_W(ctx);
			case "Q":
			case "QUIT":
				return CMD_Q(ctx);
		}
		return null;
	}

	private Object CMD_Q(CmdContext ctx) {
		if (ctx.exprList() != null) {
			// return result of evaluated expression
			visit(ctx.exprList().expr(0));
			curMethod.visitInsn(Opcodes.ARETURN);
			
		} else {
			// no return value, just return
			curMethod.visitInsn(Opcodes.RETURN);
		}
		return null;
	}

	private Object CMD_W(CmdContext ctx) {
		// loop through each expression, write to System.out for now 
		for (ExprContext expr : ctx.exprList().expr()) {
			curMethod.visitFieldInsn(Opcodes.GETSTATIC,
					Type.getInternalName(System.class), "out",
					Type.getDescriptor(java.io.PrintStream.class));
			visitExpr(expr);
			curMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(java.io.PrintStream.class), 
					"print", Type.getMethodDescriptor(Type.getType(Void.TYPE), Type.getType(String.class)), false);
			curMethod.visitMaxs(1,0);
			
		}
		return null;
	}
	
	@Override
	public Object visitExpr(ExprContext ctx) {
		if (ctx.literal() != null && ctx.literal().STR_LITERAL() != null) {
			String txt = ctx.literal().STR_LITERAL().getText();
			// chop off the quotes
			txt = txt.substring(1, txt.length()-1);
			curMethod.visitLdcInsn(txt);
		} else if (ctx.literal() != null) {
			curMethod.visitLdcInsn(ctx.literal().getText());
		} else if (ctx.ref() != null) {
			visitRef(ctx.ref());
		} else{
			curMethod.visitLdcInsn("<EXPR?>");
		}
		return null;
	}
	
	@Override
	public Object visitRef(RefContext ctx) {
		// first see if ref is an argument in the symbol table?
		String id = ctx.ID(0).toString();
		if (curSymbols.containsKey(id)) {
			curMethod.visitVarInsn(Opcodes.ALOAD, curSymbols.get(id)-1);
		}
		return null;
	}
	

}
