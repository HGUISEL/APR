package parser.isel.csee.edu.handong;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.eclipse.jdt.core.dom.CompilationUnit;

public class ASTParserImpl implements ASTParser {
	
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

	@Override
	public CompilationUnit run(String file) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ArrayList<String> getStatements() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
