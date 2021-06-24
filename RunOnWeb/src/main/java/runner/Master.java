package runner;

import java.io.*;
import java.lang.*; 
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.*;

class Master {
	


	public static void main(String[] args) throws IOException { 


		boolean is_d4j = false;
		String project;
		String d4j_ID;
		
		String faultyFilePath="";
		String faultyLine="";
		String faultySha="";
		String githubURL="";

		String sourceDir="";
		String targetDir="";
		String compileClassPath=""; //컴파일에 필요한 class들
		String testClassPath=""; //테스트에 필요한 class들
		String testList="";
		String buildTool="";

		String hash="";

		String firstArguments="";
		String secondArguments="";

		hash = args[0];

	

	


// process the arguments
		if(args[1].equals("d4j")){ // D4J 버그의 경우
			is_d4j = true;
			String temp[] = args[2].split("-");
			project = temp[0];
			d4j_ID = temp[1];
			firstArguments = args[2]; // ex) Math-34
		}
		else{ // custom의 경우
			String temp[] = args[2].split(",");
			
			project = temp[0];
			faultyFilePath = temp[1];
			faultyLine = temp[2];
			faultySha = temp[3];
			githubURL = temp[4]+".git";

			firstArguments = project + "," + faultyFilePath + "," + faultyLine + "," + faultySha + "," + githubURL;
			// firstArguments = args[2]; // 이렇게 해도 된다 사실..
			

			temp = args[3].split(",");
			sourceDir = temp[0];
			targetDir = temp[1];
			testList = temp[2];
			testClassPath = temp[3];
			compileClassPath = temp[4];
			buildTool = temp[5];

			secondArguments = sourceDir + "," + targetDir + "," + testList + "," + testClassPath + "," + compileClassPath + "," + buildTool ;
			// secondArguments = args[3] //이렇게 해도 됨
			
			
			

		}

		// to check the actual input from the web

		// File myObj = new File("/home/aprweb/APR_Projects/APR/target/inputs.txt");  		
		// FileWriter myWriter = new FileWriter("/home/aprweb/APR_Projects/APR/target/inputs.txt");
		// myWriter.write(firstArguments + "\n" + secondArguments);




		List<String> list;
		ProcessBuilder build; 

// run commit collector
		list = new ArrayList<String>();
        list.add("python3");
		list.add("/home/aprweb/APR_Projects/APR/pool/runner_web/commit_collector_web.py");
		list.add("-h");
		list.add(hash);
		list.add("-i");
		list.add(firstArguments);
		if(is_d4j == true){
			list.add("-d");
			list.add("true");
		}

		build = new ProcessBuilder(list);
		build.directory(new File("/home/aprweb/APR_Projects/APR"));

		// System.out.println("command: " + build.command()); 
		// myWriter.write("\n"+build.command());

        Process run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run change vector collector
		list.clear();
		list.add("python3");
		list.add("/home/aprweb/APR_Projects/APR/pool/runner_web/change_vector_collector_web.py");
		list.add("-h");
		list.add(hash);
		if(is_d4j == true){
			list.add("-d");
			list.add("true");
		}

		build = new ProcessBuilder(list);
		build.directory(new File("/home/aprweb/APR_Projects/APR"));

		// System.out.println("command: " + build.command()); 
		// myWriter.write("\n"+build.command());

        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run simfin
		list.clear();
        list.add("python3");
		list.add("/home/aprweb/APR_Projects/APR/pool/simfin/gv_ae_web.py");
		list.add("-p");
		list.add("test");
		list.add("-k");
		list.add("10"); // # -p means predict, -t means train; -k is for top-k neighbors
		list.add("-h");
		list.add(hash);

		build = new ProcessBuilder(list);
		build.directory(new File("/home/aprweb/APR_Projects/APR"));

		// System.out.println("command: " + build.command());
		// myWriter.write("\n"+build.command());

        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run prepare pool source 
		list.clear();
        list.add("python3");
		list.add("/home/aprweb/APR_Projects/APR/pool/runner_web/prepare_pool_source_web.py");
		list.add("-h");
		list.add(hash);

		build = new ProcessBuilder(list);
		build.directory(new File("/home/aprweb/APR_Projects/APR"));

		// System.out.println("command: " + build.command()); 
		// myWriter.write("\n"+build.command());

        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run confix
		list.clear();
        list.add("python3");
		list.add("/home/aprweb/APR_Projects/APR/confix/run_confix_web.py");
		list.add("-h");
		list.add(hash);
		if(is_d4j == true){
			list.add("-d");
			list.add("true");
		}
		else{
			list.add("-i");
			list.add(secondArguments);
		}
		
        
		build = new ProcessBuilder(list);
		build.directory(new File("/home/aprweb/APR_Projects/APR"));

		// System.out.println("command: " + build.command());
		// myWriter.write("\n"+build.command());

        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


		// myWriter.close();


		return;
				
	}

	
}
