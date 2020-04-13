package parser.isel.csee.edu.handong;

//import java.io.File;
//import java.util.List;

//import util.isel.csee.edu.handong.*;

public class StringParserTest {

	public static void main(String[] args) {
        String sample = "org.apache.commons.math.distribution$NormalDistributionTest#testMath280():168;1.0";
        
        StringParser sp = new StringParser();
                  
        int lineNum = sp.parseLineNum(sample);
        String methodName = sp.parseMethodName(sample);
        String filePath = sp.parseFilePath(sample);
        System.out.println(lineNum+" ");
        System.out.println(methodName+" ");
        System.out.println(filePath+" ");
      }
    

}
