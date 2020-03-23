package parser.isel.csee.edu.handong;



import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class StringParser {
    
    
    public StringParser(){
        
    }

    public void stringParserRunner(){

    }// 이 클래스에 전체적인 흐름을 잡아주는 메서드. csv파일을 한 줄씩 읽으면서 아래 메서드들을 실행한다.
    
    public String parseMethodName(String src){
        StringTokenizer st1 = new StringTokenizer(src);
        st1.nextToken("#");
        src = st1.nextToken("#");
        StringTokenizer st2 = new StringTokenizer(src);
        src = st2.nextToken(":");
        

        return src;

    }// 입력으로 들어온 스트링에서 메서드 이름에 해당하는 부분을 파싱해서 스트링으로 반환한다.
    
    public String parseFilePath(String src){
        String filePath="";
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

        filePath += ".class";

        return filePath;

    }// 입력으로 들어온 스트링에서 패키지이름,클래스이름을 파싱한 뒤, 두 문자열을 가공해서 클래스파일이 위치한 경로로 만들어 스트링 형태로 반환한다.
    
    public int parseLineNum(String src){
        StringTokenizer st1 = new StringTokenizer(src);
        st1.nextToken(":");
        src = st1.nextToken(":");
        StringTokenizer st2 = new StringTokenizer(src);
        src = st2.nextToken(";");
        int lineNum = Integer.parseInt(src);

        return lineNum;


    }// 입력으로 들어온 스트링에서 라인넘버에 해당하는 값을 파싱해서 int형태로 반환한다.
