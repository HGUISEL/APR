package parser.isel.csee.edu.handong;



import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class StringParser {
    
    
    public StringParser(){
        
    }

    public void stringParserRunner(){

    }// method that controls the flow. read csv file line by line and runs the methods listed below.
    // However, not used since we do this directly on DataProcessor.DataProcessorRuner()
    
    public String parseMethodName(String src){
        StringTokenizer st1 = new StringTokenizer(src);
        st1.nextToken("#");
        src = st1.nextToken("#");
        StringTokenizer st2 = new StringTokenizer(src);
        src = st2.nextToken(":");
        

        return src;

    }// parse the method name part from the input string.
    
    public String parseFilePath(String src){
        String filePath=""; //target directory,the directory given as the source directory must be in here
        StringTokenizer st1 = new StringTokenizer(src);

        String dir = st1.nextToken("$");
        src = st1.nextToken("$");
        StringTokenizer st2 = new StringTokenizer(dir);
        while(st2.hasMoreTokens()){
        filePath += st2.nextToken(".");
        filePath+="/";}
        
        StringTokenizer st3 = new StringTokenizer(src);
        dir = st3.nextToken("#");
        filePath += dir;

        filePath += ".java"; //or class??

        return filePath;

    }// parse the package name and class name part from the input string, make it into a file path.
    
    public int parseLineNum(String src){
        StringTokenizer st1 = new StringTokenizer(src);
        st1.nextToken(":");
        src = st1.nextToken(":");
        StringTokenizer st2 = new StringTokenizer(src);
        src = st2.nextToken(";");
        int lineNum = Integer.parseInt(src);

        return lineNum;
    }
    // parse the line number part from the input string and return it as integer.

    
    public double parseScore(String src){
        StringTokenizer st1 = new StringTokenizer(src);
        st1.nextToken(";");
        src = st1.nextToken(";");
        StringTokenizer st2 = new StringTokenizer(src);
        src = st2.nextToken(",");
        
        double score = Double.parseDouble(src);

        return score;
    }
    // parse the score part from the input string and return it as double.
    
}
    