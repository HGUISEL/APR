package isel.csee.edu.handong;
//import Variant

import java.util.ArrayList;
import java.util.List;
import java.io.*;
import parser.isel.csee.edu.handong.*;
import util.isel.csee.edu.handong.*;

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
		ArrayList<String> csvContents = new ArrayList<>();
		ArrayList<String> filePathList = new ArrayList<>();
		ArrayList<String> methodNameList = new ArrayList<>();
	}
	
	public void DataProcessorRunner() {

		csvFileReader(); // csv file을 읽어온다
		StringParser sp = new StringParser();
	    for(String line : csvContents){
			filePathList.add(sp.parseFilePath(line));
			methodNameList.add(sp.parseMethodName(line));
			scoreList.add(sp.parseScore(line));
        }// use StringParser() // 유효한 점수가 존재하는 항목들의 file path, method name, line number로 쪼개기 ; score는 statement에 붙어야 하기에 MethodParser에서 따로 가져온다.
		
		// use ASTParser() // statement 뽑아서 this.statements에 넣기, 문제 파일의 AST 만들고 반환 후 종료
	    createInitialVariant(); // 지금까지 모은 것들로 Variant 생성.
	    // 위에 호출된 메소드 안에서 MethodParser(method name) // Method별로 statement와 score 추출해서 statement line number대로 정렬된 score만 반환,
	}
	
	private void csvFileReader() {
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
	
	 public Variant createInitialVariant() { //need to implement Variant class
		Variant newVariant = new Variant();
		String faultyFilePath;//가상의 faulty file이 있다고 가정
		File faultyFile = new File(faultyFilePath);
		JavaCode2String c2sParser = new JavaCode2String();
		String faultyFileContents = c2sParser.FiletoString(faultyFile);//file을 String으로 변환

		JavaASTParser AP = new JavaASTParser(faultyFileContents);//ASTParser 인스턴스를 위에서 만든 String으로 생성

		newVariant.setAST(AP.run(faultyFileContents));
		newVariant.setStatementList(AP.getStatements());//AP에서 필요한 것들 뽑아냄
		
		ArrayList<Double> scores = new ArrayList<>();
		for (String name : methodNameList) {
	    	scores.addAll(MethodParser(name)); //score 부분 어떻게 처리?, methodParser는 이제 버리는 건가?
		}
		newVariant.setScoreList(scores);
		//or
		//newVariant.setScoreList(scoreList);

		return newVariant;
	    
	}
	
	 public ArrayList<String> getFileList(){
		return filePathList;
	}

	public String getTargetPath() {
		return targetPath;
	}

	public String getCsvPath() {
		return csvPath;
	}

	public ArrayList<String> getCsvContents() {
		return csvContents;
	}

	public ArrayList<String> getFilePathList() {
		return filePathList;
	}

	public ArrayList<String> getMethodNameList() {
		return methodNameList;
	}

	public ArrayList<String> getStatements() {
		return statements;
	}

	public ArrayList<Double> getScoreList() {
		return scoreList;
	}

}
