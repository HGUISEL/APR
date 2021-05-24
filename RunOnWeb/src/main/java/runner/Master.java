package runner;

import java.io.*;
import java.lang.*; 
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.*;

class Master { 
	public static void main(String[] arg) throws IOException { 
		// processs 목록을 생성
		List<String> list = new ArrayList<String>(); 


		list = new ArrayList<String>(); 
        list.add("python3");
		list.add("./pool/runner_web/commit_collector_web.py");
		list.add("-d");
		list.add("true");

		ProcessBuilder build = new ProcessBuilder(list);
		build.directory(new File("/home/goodtaeeun/APR_Projects/APR"));

		// 프로세스 빌더 인스턴스에 저장된 커맨드 확인 
		System.out.println("command: " + build.command()); 
        Process run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}

		list = new ArrayList<String>();
		list.add("python3");
		list.add("./pool/runner_web/change_vector_collector_web.py");
		list.add("-d");
		list.add("true");

		build = new ProcessBuilder(list);
		build.directory(new File("/home/goodtaeeun/APR_Projects/APR"));

		// 프로세스 빌더 인스턴스에 저장된 커맨드 확인 
		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


		list = new ArrayList<String>();
        list.add("python3");
		list.add("./pool/simfin/gv_ae.py");
		list.add("-p");
		list.add("test");
		list.add("-k");
		list.add("10"); // # -p means predict, -t means train; -k is for top-k neighbors

		build = new ProcessBuilder(list);
		build.directory(new File("/home/goodtaeeun/APR_Projects/APR"));

		// 프로세스 빌더 인스턴스에 저장된 커맨드 확인 
		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}


		list = new ArrayList<String>();
        list.add("python3");
		list.add("./pool/runner_web/prepare_pool_source_web.py");

		build = new ProcessBuilder(list);
		build.directory(new File("/home/goodtaeeun/APR_Projects/APR"));

		// 프로세스 빌더 인스턴스에 저장된 커맨드 확인 
		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}

		list = new ArrayList<String>();
        list.add("python3");
		list.add("./confix/run_confix_web.py");
		list.add("-d");
		list.add("true");
        
		build = new ProcessBuilder(list);
		build.directory(new File("/home/goodtaeeun/APR_Projects/APR"));

		// 프로세스 빌더 인스턴스에 저장된 커맨드 확인 
		System.out.println("command: " + build.command()); 
        run = build.start();
		try{ run.waitFor(); }
		catch(InterruptedException e){
			e.printStackTrace();
		}

		return;
				
	}

	
}