package parser.isel.csee.edu.handong;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import util.isel.csee.edu.handong.CodePreprocessor;

public class JavaASTParser {
	
	private CompilationUnit cUnit;
	private String srcCode; // source code is in this variable.
	private ArrayList<String> csvContents = new ArrayList<String>() ;
	
	public JavaASTParser(String source, ArrayList<String> csvContents){
		this.srcCode = source;
		this.csvContents = csvContents;
	}
	
	public CompilationUnit run() {//�솢 �뿬湲� �삉 source瑜� 諛쏅뒗 嫄곗�?
		ASTParser parser = ASTParser.newParser(AST.JLS12);
		
		/* settings to create AST */
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		char[] content = srcCode.toCharArray();
		parser.setSource(content);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
				JavaCore.VERSION_1_7);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		String[] sources = {};
		String[] classPaths = {};
		parser.setEnvironment(classPaths, sources, null, true);
		parser.setResolveBindings(false);
		parser.setCompilerOptions(options);
		parser.setIgnoreMethodBodies(true);
		parser.setStatementsRecovery(true);
		
		/* create AST */
		try {
			final CompilationUnit unit = (CompilationUnit) parser.createAST(null);			
			cUnit = unit;
		} catch (Exception e) {
			System.out.println("\nError while executing compilation unit : " + e.toString());
		} 
		
		return cUnit;
	}

	
	/* method to get list of methods from AST */
	public ArrayList<String> getMethods() {
		// TODO Auto-generated method stub
		ArrayList<String> lstMethodDeclaration = new ArrayList<String>();
		try {
			cUnit.accept(new ASTVisitor() {
				public boolean visit(final MethodDeclaration node) {
					/* visit all MethodDeclaration nodes and process the unnecessary parts with CodePreprocessor */
					CodePreprocessor cp = new CodePreprocessor(node.toString());
					String methodName = cp.run();
				
					lstMethodDeclaration.add(methodName);
					return super.visit(node);
				}
				public boolean visit(final TypeDeclaration node) {
					System.out.println(node.getName());
					return super.visit(node);
				}
			});
		} catch (Exception e) {
			System.out.println("Problem : " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}
		
	
		return lstMethodDeclaration;
	}
	
	public String getFullClassName() {
		StringBuilder FullName = new StringBuilder();
		try {
			cUnit.accept(new ASTVisitor() {
				/* get package name */
				public boolean visit(final PackageDeclaration node) {
					FullName.append(node.getName().toString());
					FullName.append('$'); // add gzoltar delimeter for package name
					return super.visit(node);
				}
				/* get class name */
				public boolean visit(final TypeDeclaration node) {
					FullName.append(node.getName().toString()); // concat package name and class name to make fully qualified name.
					return super.visit(node);
				}		
			});
		} catch (Exception e) {
			System.out.println("Problem : " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}
		System.out.println(FullName.toString());
		return FullName.toString();
	}
	
	/* method to make list of statements in the file */
	public ArrayList<String> getStatements(){
		String fullClassName = getFullClassName(); // get full class name to search in csv 
		ArrayList<String> lstStatement = new ArrayList<String>();
		TreeSet<Integer> lineNumbers = new TreeSet<>();
		HashMap<Integer, Double> scorePerLine = new HashMap<>();
		
		/* iterate all methods and read line number from csv content */ 
		for (String csv : csvContents) {
			if(csv.contains(fullClassName)) {
				int start = csv.indexOf(':');
				int end =  csv.indexOf(';', start);
				String lineNumber = csv.substring(start, end);
				String score = csv.substring(end);
				lineNumbers.add(Integer.parseInt(lineNumber));
				scorePerLine.put(Integer.parseInt(lineNumber), Double.parseDouble(score));
			}
		}
		
		/* get all lines match with the line number we extract from gzoltar result csv */
		String[] lines = srcCode.split(System.getProperty("\n"));
		
		for (int i = 0 ; i < lines.length ; i++) {
			if (lineNumbers.contains(i+1)) {
				lstStatement.add(lines[i]);
			}
		}
		
		return lstStatement;
	}
	
	public HashMap<Integer, Double> getScoreMap(){
		String fullClassName = getFullClassName(); // get full class name to search in csv 
		TreeSet<Integer> lineNumbers = new TreeSet<>();
		HashMap<Integer, Double> scorePerLine = new HashMap<>();
		
		/* iterate all methods and read line number from csv content */ 
		for (String csv : csvContents) {
			if(csv.contains(fullClassName)) {
				int start = csv.indexOf(':');
				int end =  csv.indexOf(';', start);
				String lineNumber = csv.substring(start, end);
				String score = csv.substring(end);
				lineNumbers.add(Integer.parseInt(lineNumber));
				scorePerLine.put(Integer.parseInt(lineNumber), Double.parseDouble(score));
			}
		}
		
		return scorePerLine;
	}
	
	public ArrayList<Double> getScoreList(int size, Map<Integer, Double> scoreMap){
		ArrayList<Double> scoreList = new ArrayList<>();
		
		for(int i = 0 ; i < size; i++) {
			if (scoreMap.get(i) != null) {
				scoreList.add(scoreMap.get(i));
			}
		}
		return scoreList;
	}
	
}
