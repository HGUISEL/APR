package parser.isel.csee.edu.handong;

import java.io.File;
import java.util.List;

import util.isel.csee.edu.handong.*;

public class test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JavaCode2String J2S = new JavaCode2String();
		File file = new File("/Users/yeohunjeon/Desktop/ISEL/APR/dataset/Math-issue-340/src/main/java/org/apache/commons/math/fraction/BigFraction.java");
		String source = J2S.FiletoString(file);
		CodePreprocessor cp = new CodePreprocessor(source);
		source = cp.run();
	
		JavaASTParserImpl ast = new JavaASTParserImpl(source);
		List<String> c = ast.fileReader("/Users/yeohunjeon/Desktop/ISEL/APR/dataset/Math-issue-340/src/main/java/org/apache/commons/math/fraction/BigFraction.java");
		
		
		System.out.println("====================================");
		/*for (String str : c) {
			System.out.println(str);
		}*/

		ast.getStatements();
	}

}
