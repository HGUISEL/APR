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
	private ArrayList<String> csvContents = new ArrayList<String>() ;
	private ArrayList<String> filePathList = new ArrayList<String>();
	private ArrayList<String> methodNameList = new ArrayList<String>();
	
	public ArrayList<String> statements = new ArrayList<String>();
	public ArrayList<Double> scoreList = new ArrayList<Double>();

	//Methods
	public DataProcessor(String csv) {
		csvPath = csv;
	}
	
	public void DataProcessorRunner() {

		csvFileReader(); // csv file�쓣 �씫�뼱�삩�떎
		StringParser sp = new StringParser();
	    for(String line : csvContents){
			filePathList.add(sp.parseFilePath(line));
			methodNameList.add(sp.parseMethodName(line));
			scoreList.add(sp.parseScore(line));
        }// use StringParser() // �쑀�슚�븳 �젏�닔媛� 議댁옱�븯�뒗 �빆紐⑸뱾�쓽 file path, method name, line number濡� 履쇨컻湲� ; score�뒗 statement�뿉 遺숈뼱�빞 �븯湲곗뿉 MethodParser�뿉�꽌 �뵲濡� 媛��졇�삩�떎.
		
	    
	    
		// use ASTParser() // statement 戮묒븘�꽌 this.statements�뿉 �꽔湲�, 臾몄젣 �뙆�씪�쓽 AST 留뚮뱾怨� 諛섑솚 �썑 醫낅즺
	    createInitialVariant(); // 吏�湲덇퉴吏� 紐⑥� 寃껊뱾濡� Variant �깮�꽦.
	    // �쐞�뿉 �샇異쒕맂 硫붿냼�뱶 �븞�뿉�꽌 MethodParser(method name) // Method蹂꾨줈 statement�� score 異붿텧�빐�꽌 statement line number��濡� �젙�젹�맂 score留� 諛섑솚,
	}
	
	public void csvFileReader() {
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
	 //�뿬湲곗꽌 csvContents�뿉 �젒洹쇳븯硫� nullpointer �삤瑜섍� �쑍�떎... �솢�씪源�? �뼱夷뗫뱺 �븳 釉붾줉�뿉 �꽔�뼱�꽌 �떎�뻾�븯硫� 寃곌낵�뒗 �굹�삩�떎. 肄붾뱶�옄泥댁뿉�뒗 臾몄젣 �뾾�쓬. �떎留� ArrayList瑜� �븘�뱶濡� 二쇨퀬 珥덇린�솕 �븯�뒗寃� �븘吏� �젣��濡� 紐⑤Ⅴ�뒗 寃� 媛숇떎.
	
	 public Variant createInitialVariant() { //need to implement Variant class
		Variant newVariant = new Variant();
		String faultyFilePath = "";//change this into iterative way to find all the faulty files.
		File faultyFile = new File(faultyFilePath);
		JavaCode2String c2sParser = new JavaCode2String();
		String faultyFileContents = c2sParser.FiletoString(faultyFile);//file�쓣 String�쑝濡� 蹂��솚

		JavaASTParser AP = new JavaASTParser(faultyFileContents);//ASTParser �씤�뒪�꽩�뒪瑜� �쐞�뿉�꽌 留뚮뱺 String�쑝濡� �깮�꽦

		newVariant.setAST(AP.run(faultyFileContents));
		newVariant.setStatementList(AP.getStatements());//AP�뿉�꽌 �븘�슂�븳 寃껊뱾 戮묒븘�깂
		
		//ArrayList<Double> scores = new ArrayList<>();
		//for (String name : methodNameList) {
	    //	scores.addAll(MethodParser(name)); //score 遺�遺� �뼱�뼸寃� 泥섎━?, methodParser�뒗 �씠�젣 踰꾨━�뒗 嫄닿�?
		//}
		newVariant.setScoreList(scoreList);

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
