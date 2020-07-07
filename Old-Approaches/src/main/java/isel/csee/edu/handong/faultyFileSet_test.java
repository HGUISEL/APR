package isel.csee.edu.handong;

//import java.util.ArrayList;
import java.util.HashSet;

public class faultyFileSet_test {
	
	public static void main(String[] args) {
		String csvPath = "C:/Users/goodt/Downloads/ochiai.ranking.csv";
		DataProcessor dp = new DataProcessor(csvPath);
		dp.csvFileReader();
		
		HashSet<String> contents = new HashSet<String>();
		contents = dp.getFaultyFileSet();
		
		for(String s : contents) {
			System.out.println(s);
		}
		
		
		
	}
 

}
