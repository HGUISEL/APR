package isel.csee.edu.handong;

import java.util.ArrayList;

public class testCsvFileReader {
	
	public static void main(String[] args) {
		String csvPath = "C:/Users/goodt/Downloads/ochiai.ranking.csv";
		
		DataProcessor dp = new DataProcessor("C:/Users/goodt/Documents/Workspace/APR/dataset/Math-issue-280");
		//DataProcessor dp = new DataProcessor("C:/Users/goodt/Documents/Workspace/gzoltar/com.gzoltar.cli.examples");
		
		dp.csvFileReader();
		
		ArrayList<String> contents = new ArrayList<String>();
		contents = dp.getCsvContents();
		
		for(String s : contents) {
			System.out.println(s);
		}
		
		dp.DataProcessorRunner();
		
		
		
	}
 
}

