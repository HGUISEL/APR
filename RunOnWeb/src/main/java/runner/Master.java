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
		String gitURL="";

		String firstArguments="";


// process the arguments
		if(args[0].equals("d4j")){ // D4J 버그의 경우
			is_d4j = true;
			String temp[] = args[1].split("-");
			project = temp[0];
			d4j_ID = temp[1];
			firstArguments = args[1]; // ex) Math-34
		}
		else{
			String temp[] = args[1].split(",");
			
			faultyFilePath = temp[0];
			faultyLine = temp[1];
			faultySha = temp[2];
			gitURL = temp[3];

			project = gitURL.substring(gitURL.lastIndexOf('/'), gitURL.indexOf(".git"));

			firstArguments = project + "," + faultyFilePath + "," + faultyLine + "," + faultySha;
			

			temp = args[2].split(",");
			// deal with the remainders
		}




		List<String> list;
		ProcessBuilder build; 

// run commit collector
		list = new ArrayList<String>();
        list.add("python3");
		list.add("/home/DPMiner/APR_Contents/APR/pool/runner_web/commit_collector_web.py");
		list.add("-i");
		list.add(firstArguments);
		if(is_d4j == true){
			list.add("-d");
			list.add("true");
		}

		build = new ProcessBuilder(list);
		build.directory(new File("/home/DPMiner/APR"));

		System.out.println("command: " + build.command()); 
        Process run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run change vector collector
		list.clear();
		list.add("python3");
		list.add("/home/DPMiner/APR_Contents/APR/pool/runner_web/change_vector_collector_web.py");
		if(is_d4j == true){
			list.add("-d");
			list.add("true");
		}

		build = new ProcessBuilder(list);
		build.directory(new File("/home/DPMiner/APR"));

		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run simfin
		list.clear();
        list.add("python3");
		list.add("/home/DPMiner/APR_Contents/APR/pool/simfin/gv_ae_web.py");
		list.add("-p");
		list.add("test");
		list.add("-k");
		list.add("10"); // # -p means predict, -t means train; -k is for top-k neighbors

		build = new ProcessBuilder(list);
		build.directory(new File("/home/DPMiner/APR"));

		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run prepare pool source 
		list.clear();
        list.add("python3");
		list.add("/home/DPMiner/APR_Contents/APR/pool/runner_web/prepare_pool_source_web.py");

		build = new ProcessBuilder(list);
		build.directory(new File("/home/DPMiner/APR"));

		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


// run confix
		list.clear();
        list.add("python3");
		list.add("/home/DPMiner/APR_Contents/APR/confix/run_confix_web.py");
		if(is_d4j == true){
			list.add("-d");
			list.add("true");
		}
        
		build = new ProcessBuilder(list);
		build.directory(new File("/home/DPMiner/APR"));

		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


		return;
				
	}

	
}
