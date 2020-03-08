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
	
	CompilationUnit cUnit;
	String source;
	ArrayList<Statement> lstStatement = new ArrayList<Statement>();
	
	@Override
	public CompilationUnit run(String source) {
		// TODO Auto-generated method stub
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
			try {
				unit.accept(new ASTVisitor() {

					public boolean visit(final Statement node) {
						lstStatement.add(node);
						//return super.visit(node);
					}
					
				});
			} catch (Exception e) {
				System.out.println("Problem : " + e.toString());
				e.printStackTrace();
				System.exit(0);
			}

		} catch (Exception e) {
			System.out.println("\nError while executing compilation unit : " + e.toString());
		}
		
		// I couldn't find solution to get the whole statement node at once. 
		// There is a way to get statements by visiting all child kind of statement but it does not prove us the order because each visiting for the one kind of node executes once at a time.
		// I might need more time to find solution for this problem.
		
		return cUnit;
	}

	@Override
	public ArrayList<String> getStatements() {
		// TODO Auto-generated method stub
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
