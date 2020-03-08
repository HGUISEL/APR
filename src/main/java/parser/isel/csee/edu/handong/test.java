package parser.isel.csee.edu.handong;

import java.util.List;

public class test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JavaASTParserImpl ast = new JavaASTParserImpl();
		List<String> c = ast.fileReader("/Users/yeohunjeon/Desktop/ISEL/APR/dataset/Math-issue-280/src/java/org/apache/commons/math/ArgumentOutsideDomainException.java");
		
		for (String str : c) {
			System.out.println(str);
		}

	}

}
