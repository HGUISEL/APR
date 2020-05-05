package isel.csee.edu.handong;
//import Variant

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.*;
import parser.isel.csee.edu.handong.*;
import util.isel.csee.edu.handong.*;

public class DataProcessor {
	//fields
	private String targetPath;
	private String csvPath;
	private ArrayList<String> filePathList = new ArrayList<String>();
	private ArrayList<String> methodNameList = new ArrayList<String>();
	private ArrayList<String> csvContents = new ArrayList<String>() ;
	
	public ArrayList<String> statements = new ArrayList<String>();
	public ArrayList<Double> scoreList = new ArrayList<Double>();

	//Methods
	public DataProcessor(String csv) {
		csvPath = csv;
	}
	
	public void DataProcessorRunner() {

		csvFileReader(); // read the csv file
		StringParser sp = new StringParser();
	    for(String line : csvContents){
			filePathList.add(sp.parseFilePath(line));
			methodNameList.add(sp.parseMethodName(line));
			scoreList.add(sp.parseScore(line));
        }// using StringParser. split the line with valid score into file path, method name, line number.
		
	    
	    
	    // use ASTParser() //  make AST and extract statements from the problematic file and put it in this.statements
	    createInitialVariant(); 
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
	 
	 public Variant createInitialVariant() { //need to implement Variant class
		Variant newVariant = new Variant();
		String faultyFilePath = "";//change this into iterative way to find all the faulty files.
		File faultyFile = new File(faultyFilePath);
		JavaCode2String c2sParser = new JavaCode2String();
		String faultyFileContents = c2sParser.FiletoString(faultyFile);// convert the contents of file into a string.

		JavaASTParser AP = new JavaASTParser(faultyFileContents, csvContents);//create ASTParser instance with the string just converted.

		newVariant.setAST(AP.run());
		ArrayList<String> statementList = AP.getStatements();
		newVariant.setStatementList(statementList);// extracted data needed from the AP
		
		
		
		newVariant.setScoreList(AP.getScoreList(statementList.size(), AP.getScoreMap()));

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
