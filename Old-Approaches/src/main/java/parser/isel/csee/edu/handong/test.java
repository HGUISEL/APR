package parser.isel.csee.edu.handong;

import java.io.File;

import util.isel.csee.edu.handong.*;

public class test {

	public static void main(String[] args) {
		JavaCode2String J2S = new JavaCode2String();
		File file = new File("/Users/yeohunjeon/Desktop/ISEL/APR/dataset/Math-issue-340/src/main/java/org/apache/commons/math/fraction/BigFraction.java");
		String source = J2S.FiletoString(file);
	
		JavaASTParser ast = new JavaASTParser(source);
		
		ast.getFullClassName();
	}

}
