package parser.isel.csee.edu.handong;

import java.util.*;

import org.eclipse.jdt.core.dom.*;

import isel.csee.edu.handong.DataProcessor;
import util.isel.csee.edu.handong.CodePreprocessor;

import org.eclipse.jdt.core.JavaCore;

public class JavaASTParser {
	
	private CompilationUnit cUnit;
	private String srcCode; // source code is in this variable.
	
	JavaASTParser(String source){
		this.srcCode = source;
		ASTParser parser = ASTParser.newParser(AST.JLS12);
		
		/* settings to create AST */
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		char[] content = source.toCharArray();
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
	}
	
	public CompilationUnit run(String source) {//왜 여기 또 source를 받는 거지?
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
		ArrayList<String> csvContent = DataProcessor.getCsvContents(); // we need to get csv contents to look up line numbers of given methods 
		ArrayList<String> lstStatement = new ArrayList<String>();
		TreeSet<Integer> lineNumbers = new TreeSet<>();
		
		/* iterate all methods and read line number from csv content */ 
		for (String csv : csvContent) {
			if(csv.contains(fullClassName)) {
				int start = csv.indexOf(':');
				int end =  csv.indexOf(';', start);
				String lineNumber = csv.substring(start, end);
				lineNumbers.add(Integer.parseInt(lineNumber));
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
	
}
