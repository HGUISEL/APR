package isel.csee.edu.handong;
//import Variant

import java.util.ArrayList;
import java.util.List;
import java.io.*;
import parser.isel.csee.edu.handong.*;


public class DataProcessor {
	//fields
	private String targetPath;
	private static String csvPath;
	private static ArrayList<String> csvContents;
	private static ArrayList<String> filePathList;
	private static ArrayList<String> methodNameList;
	
	public ArrayList<String> statements;
	public ArrayList<Double> scoreList;

	//Methods
	public DataProcessor(String csv) {
		csvPath = csv;
		ArrayList<String> csvContents = new ArrayList<>();
		ArrayList<String> filePathList = new ArrayList<>();
		ArrayList<String> methodNameList = new ArrayList<>();
	}
	
	public void DataProcessorRunner() {

	    csvFileReader(); // csv file을 읽어온다
	    for(String line : csvContents){
			StringParser sp = new StringParser();
            filePathList.add(sp.parseFilePath(line));
            methodNameList.add(sp.parseMethodName(line));

        }// use StringParser() // 유효한 점수가 존재하는 항목들의 file path, method name, line number로 쪼개기 ; score는 statement에 붙어야 하기에 MethodParser에서 따로 가져온다.
	    // use ASTParser() // statement 뽑아서 this.statements에 넣기, 문제 파일의 AST 만들고 반환 후 종료
	    createInitialVariant(); // 지금까지 모은 것들로 Variant 생성.
	    // 위에 호출된 메소드 안에서 MethodParser(method name) // Method별로 statement와 score 추출해서 statement line number대로 정렬된 score만 반환,
	}
	
	private static void csvFileReader() {
		try {
			File file = new File(csvPath);
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			String line = "";
			while((line = br.readLine()) != null) {
			  csvContents.add(line);
		   }
		   br.close();
		   } catch(IOException ioe) {
			  ioe.printStackTrace();
		   }
	 }
	 //여기서 csvContents에 접근하면 nullpointer 오류가 뜬다... 왜일까? 어쨋든 한 블록에 넣어서 실행하면 결과는 나온다. 코드자체에는 문제 없음. 다만 ArrayList를 필드로 주고 초기화 하는게 아직 제대로 모르는 것 같다.
	
	public ArrayList<String> getFileList(){
		return filePathList;
	}

	public Variant createInitialVariant() { //need to implement Variant class
		Variant newVariant = new Variant(); 
	    newVariant.AST = JavaASTParser();
	    newVariant.statementList = this.statements
	    for (String name : methodNameList) {
	    	Variant.scoreList.append(MethodParser(method name));
	    }
	}


	public String getTargetPath() {
		return targetPath;
	}

	public static String getCsvPath() {
		return csvPath;
	}

	public static ArrayList<String> getCsvContents() {
		return csvContents;
	}

	public static ArrayList<String> getFilePathList() {
		return filePathList;
	}

	public static ArrayList<String> getMethodNameList() {
		return methodNameList;
	}

	public ArrayList<String> getStatements() {
		return statements;
	}

	public ArrayList<Double> getScoreList() {
		return scoreList;
	}

}
