package isel.csee.edu.handong;
//import Variant

import java.util.ArrayList;
import java.util.List;
import java.io.*;


public class DataProcessor {
	//fields
	private String targetPath;
	private String csvPath;
	private ArrayList<String> csvContents;
	private ArrayList<String> filePathList;
	private ArrayList<String> methodNameList;
	
	public ArrayList<String> statements;
	public ArrayList<Double> scoreList;

	//Methods
	public DataProcessor(String csv) {
		csvPath = csv;
	}
	
	public void DataProcessorRunner() {

	    csvContents = csvFileReader(); // csv file을 읽어온다
	    for(String line : csvContents){
            filePathList.add(StringParser.parseFilePath(line));
            methodNameList.add(StringParser.parseMethodName(line));

        }// use StringParser() // 유효한 점수가 존재하는 항목들의 file path, method name, line number로 쪼개기 ; score는 statement에 붙어야 하기에 MethodParser에서 따로 가져온다.
	    // use ASTParser() // statement 뽑아서 this.statements에 넣기, 문제 파일의 AST 만들고 반환 후 종료
	    createInitialVariant(); // 지금까지 모은 것들로 Variant 생성.
	    // 위에 호출된 메소드 안에서 MethodParser(method name) // Method별로 statement와 score 추출해서 statement line number대로 정렬된 score만 반환,
	}
	
	private ArrayList<String> csvFileReader(){
		//use csvPath, read line by line from csv, put it in a list.
		BufferedReader in = new BufferedReader(new FileReader(csvPath));
		String str;
		while(true) {
			str=in.readLine();
			if(str==null) break;
			
			csvContents.add(str);
		}//based on implementation of text file, not sure about csv file.
		
		in.close();
	}
	
	public ArrayList<String> getFileList(){
		return filePathList;
	}
	public Variant createInitialVariant() { //need to implement Variant class
		Variant newVariant = new Variant(); 
	    newVariant.AST = JavaASTParser();
	    newVariant.statements = this.statements
	    for (String name : methodNameList) {
	    	Variant.scoreList.append(MethodParser(method name));
	    }
	}

}
