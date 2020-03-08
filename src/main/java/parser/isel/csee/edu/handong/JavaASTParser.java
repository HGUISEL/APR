package parser.isel.csee.edu.handong;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

interface JavaASTParser {
	List<String> fileReader(String filepath);
	CompilationUnit run (String file);
	ArrayList<String> getStatements();

}
