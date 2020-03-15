package parser.isel.csee.edu.handong;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.JavaCore;

public class JavaASTParserImpl implements JavaASTParser {
	
	private CompilationUnit cUnit;
	
	JavaASTParserImpl(String source){
		ASTParser parser = ASTParser.newParser(AST.JLS12);

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
		parser.setIgnoreMethodBodies(false);
		parser.setStatementsRecovery(true);
		
		try {
			final CompilationUnit unit = (CompilationUnit) parser.createAST(null);			
			cUnit = unit;
		} catch (Exception e) {
			System.out.println("\nError while executing compilation unit : " + e.toString());
		}
		
		// I couldn't find solution to get the whole statement node at once. 
		// There is a way to get statements by visiting all child kind of statement but it does not prove us the order because each visiting for the one kind of node executes once at a time.
		// I might need more time to find solution for this problem.
		
		// --> 2020.03.15. I might able to solve this problem. JC advise me to get all the statements first, and get their line number information by using the ast parser, but I found it is more convenient to collect method declaration first, and get all the body lines except single parenthesis or blank chrarcter. 
	}
	
	@Override
	public CompilationUnit run(String source) {
		// TODO Auto-generated method stub
		return cUnit;
	}

	@Override
	public ArrayList<String> getStatements() {
		// TODO Auto-generated method stub
		ArrayList<MethodDeclaration> lstMethodDeclaration = new ArrayList<MethodDeclaration>();
		// create visitor and get all the MethodDeclaration nodes
		try {
			cUnit.accept(new ASTVisitor() {
				public boolean visit(final MethodDeclaration node) {
					//System.out.println(node);
					lstMethodDeclaration.add(node);
					return super.visit(node);
				}
				
			});
		} catch (Exception e) {
			System.out.println("Problem : " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}
		
		// After collecting MethodDeclaration nods, get each statement form the declaration and their body. we will skip if we have blank or single parenthesis in the line. 
	
		int cnt = 0;
		
		for (int i=0 ; i < lstMethodDeclaration.size() ; i++) {
			String[]stmt = lstMethodDeclaration.get(i).toString().split("\n");
			for (int j = 0 ; j < stmt.length ; j++) {
				System.out.println(cnt++ + " " + stmt[j]);
			}
		}
		return null;
	}
	
	@Override
	public List<String> fileReader(String filepath) {
		// TODO Auto-generated method stub
		List<String> srcCode = new ArrayList<String>();
		Path path = Paths.get(filepath);
		Charset cs = StandardCharsets.UTF_8;
		try {
			srcCode = Files.readAllLines(path, cs);
			
		}catch(IOException e) {
			e.printStackTrace();
		}
		return srcCode;
	}
	
}
