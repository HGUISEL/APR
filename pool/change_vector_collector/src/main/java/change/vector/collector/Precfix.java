//package change.vector.collector;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import org.apache.commons.csv.CSVFormat;
//import org.apache.commons.csv.CSVPrinter;
//import org.apache.commons.text.similarity.JaccardSimilarity;
//import org.apache.commons.text.similarity.LevenshteinDistance;
//import org.commoncrawl.util.shared.SimHash;
//import org.eclipse.jgit.api.errors.GitAPIException;
//
//public class Precfix {
//
//	public static void runPrecfix(Input input, ArrayList<BeforeBIC> bbics) throws IOException, GitAPIException {
//		ArrayList<DefectPatchPair> dps = new ArrayList<DefectPatchPair>();
//		ArrayList<BeforeBIC> newBeforeBICs = new ArrayList<BeforeBIC>();
//		BufferedWriter writer = new BufferedWriter(new FileWriter("./assets/code.txt"));
//		if (input.inputDirectory.contains("combined")) {
//			// combined part
//			int igniteNum = 0;
//			int luceneNum = 2273;
//			int zookeeperNum = 873;
//			int flinkNum = 25490;
//			int isisNum = 1365;
//			int mahoutNum = 5240;
//			// int oozieNum = 1124;
//			Input inputIgnite = new Input("https://github.com/apache/ignite", input.inputDirectory, input.outDirectory);
//			Input inputLucene = new Input("https://github.com/apache/lucene-solr", input.inputDirectory,
//					input.outDirectory);
//			Input inputZookeeper = new Input("https://github.com/apache/zookeeper", input.inputDirectory,
//					input.outDirectory);
//			Input inputFlink = new Input("https://github.com/apache/flink", input.inputDirectory, input.outDirectory);
//			Input inputIsis = new Input("https://github.com/apache/isis", input.inputDirectory, input.outDirectory);
//			Input inputMahout = new Input("https://github.com/apache/mahout", input.inputDirectory, input.outDirectory);
//			Input inputOozie = new Input("https://github.com/apache/oozie", input.inputDirectory, input.outDirectory);
//
//			for (int i = 0; i < bbics.size(); i++) {
//				DefectPatchPair dp;
//				if (i < igniteNum) {
//					dp = new DefectPatchPair(bbics.get(i), inputIgnite);
//				} else if (i < (igniteNum + luceneNum)) {
//					dp = new DefectPatchPair(bbics.get(i), inputLucene);
//				} else if (i < igniteNum + luceneNum + zookeeperNum) {
//					dp = new DefectPatchPair(bbics.get(i), inputZookeeper);
//				} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum) {
//					dp = new DefectPatchPair(bbics.get(i), inputFlink);
//				} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum) {
//					dp = new DefectPatchPair(bbics.get(i), inputIsis);
//				} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum) {
//					dp = new DefectPatchPair(bbics.get(i), inputMahout);
//				} else {
//					dp = new DefectPatchPair(bbics.get(i), inputOozie);
//				}
//
//				// if failed to retrieve dp instance
//				if (dp.codeBIC.equals(""))
//					continue;
//
//				// if the change is too long, skip
//				String[] tokens = dp.codeBIC.split(" ");
//				if (tokens.length > 100)
//					continue;
//
//				dps.add(dp);
//				newBeforeBICs.add(bbics.get(i));
//
//				// writing codeCommit
//				writer.write("<start>\n");
//				writer.write(dp.codeBIC);
//				writer.write("<end>\n");
//			}
//
//		} else {
//			int i = 0;
//			for (BeforeBIC bbic : bbics) {
//				DefectPatchPair dp = new DefectPatchPair(bbic, input);
//				dps.add(dp);
//
//				// if failed to retreive dp instance
//				if (dp.codeBIC.equals(""))
//					continue;
//
//				// if the change is too long, skip
//				String[] tokens = dp.codeBIC.split(" ");
//				if (tokens.length > 100)
//					continue;
//
//				dps.add(dp);
//				newBeforeBICs.add(bbics.get(i));
//
//				// writing codeCommit
//				writer.write("<start>\n");
//				writer.write(dp.codeBIC);
//				writer.write("<end>\n");
//				i++;
//			}
//		}
//
//		// get SimHash for defect-patch pairs;
////		ArrayList<ArrayList<Long>> simHashes = new ArrayList<ArrayList<Long>>();
////		simHashes = getSimHash(dps);
////
////		double[][] reducer = new double[simHashes.size()][simHashes.size()];
////		reducer = getReducers(simHashes);
////
////		double[][] similarity = new double[simHashes.size()][simHashes.size()];
////		similarity = calculateSimilarity(reducer, dps);
////
////		if (input.inFile.contains("combined")) {
////			writePrecfixMulti(input, similarity);
////		} else {
////			writePrecfix(input, similarity);
////		}
//		BeforeBIC.writeBBICsOnCSV(input, newBeforeBICs, "label.csv");
//		System.out.println("done writing commits");
//		writer.close();
//	}
//
//	public static ArrayList<ArrayList<Long>> getSimHash(ArrayList<DefectPatchPair> dps) {
//		ArrayList<ArrayList<Long>> simHashes = new ArrayList<ArrayList<Long>>();
//		for (DefectPatchPair dp : dps) {
//			String defectString = "";
////			String patchString = "";
//			for (int i = 0; i < dp.codeDefect.size(); i++) {
//				defectString += dp.codeDefect.get(i);
//			}
////			for (int i = 0; i < dp.codePatch.size(); i++) {
////				patchString += dp.codePatch.get(i);
////			}
//			long defectSH = SimHash.computeOptimizedSimHashForString(defectString);
//			System.out.println("sim: " + defectSH);
////			long patchSH = SimHash.computeOptimizedSimHashForString(patchString);
//			ArrayList<Long> dpPair = new ArrayList<Long>();
//			dpPair.add(defectSH);
////			dpPair.add(patchSH);
//			simHashes.add(dpPair);
//		}
//		System.out.println("Calculating SimHashes complete!");
//		return simHashes;
//	}
//
//	public static double[][] getReducers(ArrayList<ArrayList<Long>> simHashes) {
//		double[][] reducer = new double[simHashes.size()][simHashes.size()];
//		for (int i = 0; i < simHashes.size(); i++) {
//			for (int j = 0; j < simHashes.size(); j++) {
//				float defectHD = (float) SimHash.hammingDistance(simHashes.get(i).get(0), simHashes.get(j).get(0));
////				float patchHD = (float) SimHash.hammingDistance(simHashes.get(i).get(1), simHashes.get(j).get(1));
////				float hammingDistance = (defectHD + patchHD) / 2;
//				reducer[i][j] = defectHD;
//			}
//		}
//		System.out.println("Calculating reducers complete!");
//		return reducer;
//	}
//
//	public static double[][] calculateSimilarity(double[][] reducer, ArrayList<DefectPatchPair> dps) {
//		double[][] similarity = new double[dps.size()][dps.size()];
//		String[] defectStrings = new String[dps.size()];
////		String[] patchStrings = new String[dps.size()];
//		int dpsIndex = 0;
//
//		// concatenating ArrayList<String> of code to one String
//		for (DefectPatchPair dp : dps) {
//			String defectString = "";
////			String patchString = "";
//			for (int i = 0; i < dp.codeDefect.size(); i++) {
//				defectString += dp.codeDefect.get(i).replaceAll("\\s", "");
//				defectString += "\n";
//			}
////			for (int i = 0; i < dp.codePatch.size(); i++) {
////				patchString += dp.codePatch.get(i).replaceAll("\\s", "");
////				patchString += "\n";
////			}
//			if (dpsIndex == 45 || dpsIndex == 899)
//				System.out.println(defectString);
//			defectStrings[dpsIndex] = defectString;
////			patchStrings[dpsIndex] = patchString;
//			dpsIndex++;
//		}
//
//		double levenDefectMax = 0;
////		double levenPatchMax = 0;
//
//		for (int i = 0; i < dps.size(); i++) {
//			for (int j = 0; j < dps.size(); j++) {
//
//				if (reducer[i][j] < 17) {
//					double levenDefect = new LevenshteinDistance().apply(defectStrings[i], defectStrings[j]);
//					// double levenPatch = new LevenshteinDistance().apply(patchStrings[i],
//					// patchStrings[j]);
//					if (levenDefect > levenDefectMax)
//						levenDefectMax = levenDefect;
////					if (levenPatch > levenPatchMax)
////						levenPatchMax = levenPatch;
//				}
//			}
//			if (i > 0) {
//				System.out.print(String.format("\033[%dA", 1)); // Move up
//			}
//			System.out.println("Getting Levenshtien Max " + i + "/" + dps.size());
//		}
//		for (int i = 0; i < dps.size(); i++) {
//			for (int j = 0; j < dps.size(); j++) {
//				if (reducer[i][j] > 17) {
//					similarity[i][j] = 0.0;
//				} else {
//					if (defectStrings[i].length() < 50 || defectStrings[j].length() < 50) {
////							|| patchStrings[i].length() < 50 || patchStrings[j].length() < 50) {
//						similarity[i][j] = 0.0;
//						continue;
//					}
//
//					if (i == j) {
//						similarity[i][j] = 1.0;
//						continue;
//					}
//
//					double jaccardDefect = new JaccardSimilarity().apply(defectStrings[i], defectStrings[j]);
//					double levenshteinDefect = new LevenshteinDistance().apply(defectStrings[i], defectStrings[j]);
//					levenshteinDefect = levenshteinDefect / levenDefectMax;
//					levenshteinDefect = 1 - levenshteinDefect;
////					double jaccardPatch = new JaccardSimilarity().apply(patchStrings[i], patchStrings[j]);
////					double levenshteinPatch = 1 - (new LevenshteinDistance().apply(patchStrings[i], patchStrings[j]));
////					levenshteinPatch = levenshteinPatch / levenPatchMax;
////					levenshteinPatch = 1 - levenshteinPatch;
//					double scoreDefect = jaccardDefect * 0.8 + levenshteinDefect * 0.2;
////					double scorePatch = jaccardPatch * 0.8 + levenshteinPatch * 0.2;
////					double score = (scoreDefect + scorePatch) / 2;
//					if (i == 45 && j == 899) {
//						System.out.println(jaccardDefect);
//						System.out.println(levenshteinDefect);
//					}
//
//					similarity[i][j] = scoreDefect;
//				}
//			}
//			if (i > 0) {
//				System.out.print(String.format("\033[%dA", 1)); // Move up
//			}
//			System.out.println("Getting Scores " + i + "/" + dps.size());
//		}
//		return similarity;
//	}
//
//	public static void writePrecfixMulti(Input input, double[][] similarity) throws IOException {
//		File outFile = new File(input.outDirectory + "prec_combined7" + ".csv");
//		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile.getAbsolutePath()));
//		CSVPrinter csvprinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
//
//		// combined part
//		int igniteNum = 647;
//		int luceneNum = 1041;
//		int zookeeperNum = 294;
//		int flinkNum = 1349;
//		int isisNum = 396;
//		int mahoutNum = 386;
//		int oozieNum = 514;
//
//		// index of x-axis
//		// writing index of x-axis
//		csvprinter.print("combined7");
//		for (int i = 0; i < igniteNum; i++) {
//			csvprinter.print("ignite" + i);
//		}
//		for (int i = 0; i < luceneNum; i++) {
//			csvprinter.print("lucene-solr" + i);
//		}
//		for (int i = 0; i < zookeeperNum; i++) {
//			csvprinter.print("zookeeper" + i);
//		}
//		for (int i = 0; i < flinkNum; i++) {
//			csvprinter.print("flink" + i);
//		}
//		for (int i = 0; i < isisNum; i++) {
//			csvprinter.print("isis" + i);
//		}
//		for (int i = 0; i < mahoutNum; i++) {
//			csvprinter.print("mahout" + i);
//		}
//		for (int i = 0; i < oozieNum; i++) {
//			csvprinter.print("oozie" + i);
//		}
//		csvprinter.println();
//
//		// writing data
//		for (int i = 0, lucene = 0, zookeeper = 0, flink = 0, isis = 0, mahout = 0, oozie = 0; i < similarity.length; i++) {
//			if (i < igniteNum) {
//				csvprinter.print("ignite" + i);
//			} else if (i < (igniteNum + luceneNum)) {
//				csvprinter.print("lucene-solr" + (lucene++));
//			} else if (i < igniteNum + luceneNum + zookeeperNum) {
//				csvprinter.print("zookeeper" + (zookeeper++));
//			} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum) {
//				csvprinter.print("flink" + (flink++));
//			} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum) {
//				csvprinter.print("isis" + (isis++));
//			} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum) {
//				csvprinter.print("mahout" + (mahout++));
//			} else if (i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum + oozieNum) {
//				csvprinter.print("oozie" + (oozie++));
//			}
//			for (int j = 0; j < similarity.length; j++) {
//				if (i == j) {
//					csvprinter.print("same_instance");
//				} else if (i < igniteNum && j < igniteNum) {
//					csvprinter.print("-");
//				} else if (i > igniteNum && j > igniteNum && i < igniteNum + luceneNum && j < igniteNum + luceneNum) {
//					csvprinter.print("-");
//				} else if (i > igniteNum + luceneNum && j > igniteNum + luceneNum
//						&& i < igniteNum + luceneNum + zookeeperNum && j < igniteNum + luceneNum + zookeeperNum) {
//					csvprinter.print("-");
//				} else if (i > igniteNum + luceneNum + zookeeperNum && j > igniteNum + luceneNum + zookeeperNum
//						&& i < igniteNum + luceneNum + zookeeperNum + flinkNum
//						&& j < igniteNum + luceneNum + zookeeperNum + flinkNum) {
//					csvprinter.print("-");
//				} else if (i > igniteNum + luceneNum + zookeeperNum + flinkNum
//						&& j > igniteNum + luceneNum + zookeeperNum + flinkNum
//						&& i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum
//						&& j < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum) {
//					csvprinter.print("-");
//				} else if (i > igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum
//						&& j > igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum
//						&& i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum
//						&& j < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum) {
//					csvprinter.print("-");
//				} else if (i > igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum
//						&& j > igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum
//						&& i < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum + oozieNum
//						&& j < igniteNum + luceneNum + zookeeperNum + flinkNum + isisNum + mahoutNum + oozieNum) {
//					csvprinter.print("-");
//				} else {
//					csvprinter.print(similarity[i][j]);
//				}
//			}
//			csvprinter.println();
//		}
//		csvprinter.close();
//		System.out.println("writing precfix multi done!");
//	}
//
//	public static void writePrecfix(Input input, double[][] similarity) throws IOException {
//		File outFile = new File(input.outDirectory + "test_" + input.projectName + ".csv");
//		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile.getAbsolutePath()));
//		CSVPrinter csvprinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
//
//		csvprinter.print(input.projectName);
//		for (int i = 0; i < similarity.length; i++) {
//			csvprinter.print(i);
//		}
//		csvprinter.println();
//
//		for (int i = 0; i < similarity.length; i++) {
//			csvprinter.print(i);
//			for (int j = 0; j < similarity.length; j++) {
//				csvprinter.print(similarity[i][j]);
//			}
//			csvprinter.println();
//		}
//		csvprinter.close();
//		System.out.println("writing precfix done!");
//	}
//
//}
