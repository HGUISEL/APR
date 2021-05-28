package com.github.thwak.confix.tree;

import java.io.File;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.github.thwak.confix.util.IOUtils;

public class Parser {

	private String[] classPath = {};
	private String[] sourcePath = {};

	public Parser(String[] classPath, String[] sourcePath) {
		this.classPath = classPath;
		this.sourcePath = sourcePath;
	}

	public CompilationUnit parse(File f) {
		String source = IOUtils.readFile(f);
		//System.out.println("===== Set ASTParser ====");
		//System.out.println("0. file : " + f.getName());
		return parse(source);
	}

	public CompilationUnit parse(String source) {

		//System.out.println("	1. Source Length : " + source.length());
		//System.out.println("	2. Class Path : ");
		//for (int i = 0; i < classPath.length; i++) {
		//	System.out.println("		" + classPath[i]);
		//}
		//System.out.println("	3. Source Path : ");
		//for (int i = 0; i < sourcePath.length; i++) {
		//	System.out.println("		" + sourcePath[i]);
		//}
		ASTParser parser = ASTParser.newParser(AST.JLS8);

		parser.setEnvironment(classPath, sourcePath, null, true);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setUnitName("");
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		return cu;
	}
}
