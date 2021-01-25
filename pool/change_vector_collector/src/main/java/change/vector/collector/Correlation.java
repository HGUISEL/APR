//package change.vector.collector;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.Arrays;
//import org.apache.commons.csv.CSVFormat;
//import org.apache.commons.csv.CSVPrinter;
//import org.apache.commons.math3.ml.distance.EuclideanDistance;
//import org.apache.commons.math3.ml.distance.ManhattanDistance;
//import org.apache.commons.math3.stat.correlation.Covariance;
//import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
//import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
//import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
//import org.apache.commons.text.similarity.JaccardSimilarity;
//import weka.core.Instances;
//import weka.core.converters.ConverterUtils.DataSource;
//
//public class Correlation {
//	enum mode {
//		PEARSONS, KENDALLS, EUCLIDEAND, SPEARMANS, JACCARD, MANHATTAND, COVARIANCE, COSSIM, LCS, MY;
//	}
//
//	// runner for all
//	public static void computeAll(Input input) throws Exception {
//		if (input.inputDirectory.contains("test_")) {
//			calcCorrelationAxB(input, mode.MY);
//			System.out.println("testing all correlations done!");
//		} else {
//			calcCorrelationAxA(input, mode.MY);
//			System.out.println("writing all correlations done!");
//		}
//	}
//
//	// calculates correlation of two different set of vectors (e.g. train x test)
//	@SuppressWarnings("incomplete-switch")
//	public static void calcCorrelationAxB(Input input, mode mode) throws Exception {
//		String trainPath = "./assets/test/database3.arff";
//		String testPath = input.inputDirectory;
//
//		DataSource trainSource = new DataSource(trainPath);
//		DataSource testSource = new DataSource(testPath);
//		Instances trainset = trainSource.getDataSet();
//		Instances testset = testSource.getDataSet();
//
//		if (trainset.classIndex() == -1)
//			trainset.setClassIndex(trainset.numAttributes() - 1);
//		if (testset.classIndex() == -1)
//			testset.setClassIndex(testset.numAttributes() - 1);
//
//		int attrNum = trainset.numAttributes();
//		int trainSize = trainset.numInstances();
//		int testSize = testset.numInstances();
//
//		System.out.println("<Trainset>");
//		System.out.println("num of att: " + trainset.numAttributes());
//		System.out.println("num of inst " + trainSize);
//		System.out.println();
//		System.out.println("<Testset>");
//		System.out.println("num of att: " + testset.numAttributes());
//		System.out.println("num of inst " + testSize);
//
//		// init correlation matrix
//		ArrayList<ArrayList<Double>> cor = new ArrayList<ArrayList<Double>>();
//		for (int i = 0; i < testSize; i++) {
//			cor.add(new ArrayList<Double>());
//			for (int j = 0; j < trainSize; j++) {
//				cor.get(i).add(0.0);
//			}
//		}
//
//		// init train_arr
//		ArrayList<double[]> train_arr = new ArrayList<double[]>();
//		for (int i = 0; i < trainSize; i++) {
//			train_arr.add(new double[attrNum]);
//		}
//		for (int i = 0; i < trainSize; i++) {
//			train_arr.set(i, trainset.get(i).toDoubleArray());
//		}
//
//		// removing changeVector info before computing correlation coefficients
//		ArrayList<double[]> train_rm = new ArrayList<double[]>();
//		for (int i = 0; i < trainSize; i++) {
//			train_rm.add(new double[attrNum - 4]);
//
//		}
//		for (int i = 0; i < trainSize; i++) {
//			for (int j = 4; j < attrNum - 4; j++) {
//				train_rm.get(i)[j] = train_arr.get(i)[j];
//			}
//		}
//
//		// init test_arr
//		ArrayList<double[]> test_arr = new ArrayList<double[]>();
//		for (int i = 0; i < testset.numInstances(); i++) {
//			test_arr.add(new double[testset.numAttributes()]);
//		}
//		for (int i = 0; i < testset.numInstances(); i++) {
//			test_arr.set(i, testset.get(i).toDoubleArray());
//		}
//		// removing changeVector info before computing correlation coefficients
//		ArrayList<double[]> test_rm = new ArrayList<double[]>();
//		for (int i = 0; i < testSize; i++) {
//			test_rm.add(new double[attrNum - 4]);
//
//		}
//		for (int i = 0; i < testSize; i++) {
//			for (int j = 4; j < attrNum - 4; j++) {
//				test_rm.get(i)[j] = test_arr.get(i)[j];
//			}
//		}
//
//		switch (mode) {
//		case PEARSONS:
//			cor = calcPearsonsExclude0(input, train_rm, test_rm, cor);
//			break;
//		case KENDALLS:
//			cor = calcKendallsExclude0(input, train_rm, test_rm, cor);
//			break;
//		case EUCLIDEAND:
//			cor = calcEuclideanExclude0(input, train_rm, test_rm, cor);
//		case LCS:
//			cor = calcLCSexclude0(input, train_rm, test_rm, cor);
//			break;
//		case MY:
//			cor = calcMyCor(input, train_rm, test_rm, cor);
//			break;
//		}
//
//		writeMultiAxB(input, mode, trainSize, testSize, cor);
//		System.out.println("\nWriting " + mode + " done!\n");
//	}
//
//	// calculates correlation of instances itself (e.g. train x test)
//	@SuppressWarnings("incomplete-switch")
//	public static void calcCorrelationAxA(Input input, mode mode) throws Exception {
//
//		String filePath = input.inputDirectory;
//		DataSource source = new DataSource(filePath);
//		Instances dataset = source.getDataSet();
//
//		if (dataset.classIndex() == -1)
//			dataset.setClassIndex(dataset.numAttributes() - 1);
//
//		int dataSize = dataset.numInstances();
//		int attrNum = dataset.numAttributes();
//
//		System.out.println("num of att: " + dataset.numAttributes());
//		System.out.println("num of inst " + dataset.numInstances());
//
//		// cor instantiation
//		ArrayList<ArrayList<Double>> cor = new ArrayList<ArrayList<Double>>();
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			cor.add(new ArrayList<Double>());
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				cor.get(i).add(0.0);
//			}
//		}
//
//		// init data_arr
//		ArrayList<double[]> data_arr = new ArrayList<double[]>();
//		for (int i = 0; i < dataSize; i++) {
//			data_arr.add(new double[attrNum]);
//		}
//		for (int i = 0; i < dataSize; i++) {
//			data_arr.set(i, dataset.get(i).toDoubleArray());
//		}
//
//		// removing changeVector info before computing correlation coefficients
//		ArrayList<double[]> data_rm = new ArrayList<double[]>();
//		for (int i = 0; i < dataSize; i++) {
//			data_rm.add(new double[attrNum - 4]);
//
//		}
//		for (int i = 0; i < dataSize; i++) {
//			for (int j = 4; j < attrNum - 4; j++) {
//				data_rm.get(i)[j] = data_arr.get(i)[j];
//			}
//		}
//
//		switch (mode) {
//		case MY:
//			cor = calcMyCor(input, data_rm, cor);
//		}
//
//		// writing files
//		if (input.inputDirectory.contains("combined")) {
//			writeMultiAxA(input, mode, dataset, cor);
//		} else {
//			writeSingleAxA(input, mode, dataset, cor);
//		}
//
//		System.out.println("\nWriting " + mode + " done!\n");
//	}
//
//	// writing the result of correlation computation on csv
//	public static void writeSingleAxA(Input input, mode mode, Instances dataset, ArrayList<ArrayList<Double>> cor)
//			throws IOException {
//		File outFile = new File(input.outDirectory + mode.toString() + "_" + input.projectName + ".csv");
//		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile.getAbsolutePath()));
//		CSVPrinter csvprinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
//
//		// index of x-axis
//		csvprinter.print(mode);
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			csvprinter.print(i);
//		}
//		csvprinter.println();
//
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			csvprinter.print(i);
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				csvprinter.print(cor.get(i).get(j));
//			}
//			csvprinter.println();
//		}
//		csvprinter.close();
//	}
//
//	// writing the result of correlation computation on csv
//	public static void writeMultiAxA(Input input, mode mode, Instances dataset, ArrayList<ArrayList<Double>> cor)
//			throws IOException {
//		File outFile = new File(input.outDirectory + mode.toString() + "_combined" + ".csv");
//		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile.getAbsolutePath()));
//		CSVPrinter csvprinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
//
//		// combined part
//		int igniteNum = 647;
//		int luceneNum = 1041;
//		int zookeeperNum = 294;
//		int flinkNum = 1351;
//		int isisNum = 396;
//		int mahoutNum = 386;
//		int oozieNum = 514;
//
//		// index of x-axis
//		// writing index of x-axis
//		csvprinter.print(mode.toString());
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
//		for (int i = 0, lucene = 0, zookeeper = 0, flink = 0, isis = 0, mahout = 0, oozie = 0; i < dataset
//				.numInstances(); i++) {
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
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				if (i == j) {
//					csvprinter.print("same");
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
//					csvprinter.print(cor.get(i).get(j));
//				}
//			}
//			csvprinter.println();
//		}
//		csvprinter.close();
//	}
//
//	// writing the result of correlation computation on csv
//	public static void writeMultiAxB(Input input, mode mode, int trainSize, int testSize,
//			ArrayList<ArrayList<Double>> cor) throws IOException {
//		File outFile = new File(input.outDirectory + mode.toString() + "_test_database" + ".csv");
//
//		BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile.getAbsolutePath()));
//		CSVPrinter csvprinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
//
//		// combined part
//		int trainIgnite = 647;
//		int trainLucene = 1041;
//		int trainZookeeper = 294;
////		int trainFlink = 459;
////		int trainIsis = 124;
////		int trainMahout = 130;
////		int trainOozie = 186;
//
//		// int testIO = 2865;
//		// int testLang = 6306;
//		// int testMath = 19383;
//		int testFlink = 1351;
//		int testIsis = 396;
//		int testMahout = 386;
//		// int testOozie = 514;
//
//		String test1 = "flink";
//		String test2 = "isis";
//		String test3 = "mahout";
//		String test4 = "oozie";
//
//		// writing index of x-axis
//		csvprinter.print(mode.toString());
//		for (int i = 0; i < trainIgnite; i++) {
//			csvprinter.print("ignite" + i);
//		}
//		for (int i = 0; i < trainLucene; i++) {
//			csvprinter.print("lucene-solr" + i);
//		}
//		for (int i = 0; i < trainZookeeper; i++) {
//			csvprinter.print("zookeeper" + i);
//		}
//		csvprinter.println();
//
//		// writing correlation
//		for (int i = 0, isis = 0, mahout = 0, oozie = 0; i < testSize; i++) {
//			// writing index of y-axis
//			if (i < testFlink) {
//				csvprinter.print(test1 + i);
//			} else if (i < testFlink + testIsis) {
//				csvprinter.print(test2 + (isis++));
//			} else if (i < testFlink + testIsis + testMahout) {
//				csvprinter.print(test3 + (mahout++));
//			} else {
//				csvprinter.print(test4 + (oozie++));
//			}
//			// writing the computed correlation
//			for (int j = 0; j < trainSize; j++) {
//				csvprinter.print(cor.get(i).get(j));
//			}
//			csvprinter.println();
//		}
//		csvprinter.close();
//	}
//
//	// calculate my correlation algorithm AxA
//	static ArrayList<ArrayList<Double>> calcMyCor(Input input, ArrayList<double[]> data_arr,
//			ArrayList<ArrayList<Double>> cor) {
//
//		for (int i = 0; i < data_arr.size(); i++) {
//			for (int j = 0; j < data_arr.size(); j++) {
//
//				// calculating my correlation algo.
//				double intersection = 0.0;
//				double union = 0.0;
//
//				for (int k = 0; k < data_arr.get(0).length; k++) {
//					if (data_arr.get(j)[k] == data_arr.get(i)[k]) {
//						intersection += data_arr.get(j)[k];
//						union += data_arr.get(j)[k];
//					} else {
//						intersection += min(data_arr.get(j)[k], data_arr.get(i)[k]);
//						union += max(data_arr.get(j)[k], data_arr.get(i)[k]);
//					}
//					if (k ==  34 || k == 141 || k == 248 || k == 355
//					 || k ==  30 || k == 137 || k == 244 || k == 351
//					 || k ==  32 || k == 139 || k == 246 || k == 353 
//					 || k == 106 || k == 213 || k == 320 || k == 427 
//					 || k ==  96 || k == 203 || k == 310 || k == 417) {
//						if (data_arr.get(j)[k] > 0 && data_arr.get(i)[k] > 0) {
//							intersection += 5;
//							union += 5;
//						}
//					}
//
//				}
//
//				cor.get(i).set(j, intersection / union);
//				// System.out.println("my: " + intersection / union);
//			}
//			System.out.println(i + "/" + data_arr.size());
//		}
//		return cor;
//	}
//
//	// calculate my correlation algorithm AxB
//	static ArrayList<ArrayList<Double>> calcMyCor(Input input, ArrayList<double[]> train_arr,
//			ArrayList<double[]> test_arr, ArrayList<ArrayList<Double>> cor) {
//
//		for (int i = 0; i < test_arr.size(); i++) {
//			for (int j = 0; j < train_arr.size(); j++) {
//
//				// calculating my correlation algo.
//				double intersection = 0.0;
//				double union = 0.0;
//
//				for (int k = 0; k < train_arr.get(0).length; k++) {
//					if (train_arr.get(j)[k] == test_arr.get(i)[k]) {
//						intersection += train_arr.get(j)[k];
//						union += train_arr.get(j)[k];
//					} else {
//						intersection += min(train_arr.get(j)[k], test_arr.get(i)[k]);
//						union += max(train_arr.get(j)[k], test_arr.get(i)[k]);
//					}
//					if (k ==  34 || k == 141 || k == 248 || k == 355 
//					 || k ==  30 || k == 137 || k == 244 || k == 351
//					 || k ==  32 || k == 139 || k == 246 || k == 353 
//					 || k == 106 || k == 313 || k == 520 || k == 727 
//					 || k ==  96 || k == 203 || k == 310 || k == 417) {
//						if (train_arr.get(j)[k] > 0 && test_arr.get(i)[k] > 0) {
//							intersection += 5;
//							union += 5;
//						}
//					}
//
//				}
//
//				cor.get(i).set(j, intersection / union);
//				// System.out.println("my: " + intersection / union);
//			}
//			System.out.println(i + "/" + test_arr.size());
//		}
//		return cor;
//	}
//
//	// calculates my correlation algorithm for AxB
//	public static ArrayList<ArrayList<Double>> calcLCS(Input input, Instances trainset, Instances testset,
//			ArrayList<ArrayList<Double>> cor) {
//		for (int i = 0; i < testset.numInstances(); i++) {
//			// double test[] = testset.get(i).toDoubleArray();
//			String test = testset.get(i).toString();
//			for (int j = 0; j < trainset.numInstances(); j++) {
//				// double train[] = trainset.get(j).toDoubleArray();
//				String train = trainset.get(j).toString();
//				char[] x = test.toCharArray();
//				char[] y = train.toCharArray();
//				cor.get(i).set(j, (double) lcs(x, y, test.length(), train.length()));
//			}
//			System.out.println(i + "/" + testset.size());
//		}
//		return cor;
//	}
//
//	// calculates my correlation algorithm for AxB
//	public static ArrayList<ArrayList<Double>> calcLCSexclude0(Input input, ArrayList<double[]> train_arr,
//			ArrayList<double[]> test_arr, ArrayList<ArrayList<Double>> cor) {
//		int attrNum = train_arr.get(0).length;
//
//		for (int i = 0; i < test_arr.size(); i++) {
//			for (int j = 0; j < train_arr.size(); j++) {
//				ArrayList<Double> excludeCVtest = new ArrayList<Double>();
//				ArrayList<Double> excludeCVtrain = new ArrayList<Double>();
//
//				for (int k = 0; k < attrNum; k++) {
//					if (test_arr.get(i)[k] != 0 || train_arr.get(0)[k] != 0) {
//						excludeCVtrain.add(train_arr.get(j)[k]);
//						excludeCVtest.add(test_arr.get(i)[k]);
//					}
//				}
//				String train = excludeCVtrain.toString();
//				String test = excludeCVtest.toString();
//				int trainSize = train.length();
//				int testSize = test.length();
//
//				if (trainSize == 0 && testSize == 0) {
//					cor.get(i).set(j, 0.0);
//					continue;
//				}
//				char[] x = test.toCharArray();
//				char[] y = train.toCharArray();
//
//				int maxVal = max(trainSize, testSize);
//				double lcs = (double) lcs(x, y, testSize, trainSize) / maxVal;
//
//				cor.get(i).set(j, lcs);
//			}
//			System.out.println(i + "/" + test_arr.size());
//		}
//		return cor;
//	}
//
//	// calculates my correlation algorithm for AxA
//	public static ArrayList<ArrayList<Double>> calcLCS(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) {
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			// double test[] = testset.get(i).toDoubleArray();
//			String x = dataset.get(i).toString();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				// double train[] = trainset.get(j).toDoubleArray();
//				String y = dataset.get(j).toString();
//				char[] x1 = x.toCharArray();
//				char[] y1 = y.toCharArray();
//				cor.get(i).set(j, (double) lcs(x1, y1, x.length(), y.length()));
//				;
//			}
//			System.out.println(i + "/" + dataset.size());
//		}
//		return cor;
//	}
//
//	// calculates Pearsons for AxA
//	public static ArrayList<ArrayList<Double>> calcPearsons(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//		// Pearsons for single dim
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, new PearsonsCorrelation().correlation(x, y));
//			}
//		}
//		return cor;
//	}
//
//	// calculates Pearsons AxB fullsize
//	public static ArrayList<ArrayList<Double>> calcPearsons(Input input, Instances trainset, Instances testset,
//			ArrayList<ArrayList<Double>> cor) {
//
//		for (int i = 0; i < testset.numInstances(); i++) {
//			double test[] = testset.get(i).toDoubleArray();
//			for (int j = 0; j < trainset.numInstances(); j++) {
//				double train[] = trainset.get(j).toDoubleArray();
//				cor.get(i).set(j, new PearsonsCorrelation().correlation(test, train));
//			}
//			System.out.println(i + "/" + testset.size());
//		}
//		return cor;
//	}
//
//	// calculates Pearsons AxB remove zeros
//	public static ArrayList<ArrayList<Double>> calcPearsonsExclude0(Input input, ArrayList<double[]> train_arr,
//			ArrayList<double[]> test_arr, ArrayList<ArrayList<Double>> cor) throws Exception {
//		int attrNum = train_arr.get(0).length;
//
//		for (int i = 0; i < test_arr.size(); i++) {
//			for (int j = 0; j < train_arr.size(); j++) {
//
//				// make new changeVector that has no both zeros
//				ArrayList<Double> excludeCVtrain = new ArrayList<Double>();
//				ArrayList<Double> excludeCVtest = new ArrayList<Double>();
//
//				// traverse attributes
//				for (int k = 0; k < attrNum; k++) {
//					// if either test and train has non_zero value, add to the new CV
//					if (test_arr.get(i)[k] != 0 || train_arr.get(j)[k] != 0) {
//						excludeCVtrain.add(train_arr.get(j)[k]);
//						excludeCVtest.add(test_arr.get(i)[k]);
//					}
//				}
//
//				// changing the type to double[]
//				double[] train = new double[excludeCVtrain.size()];
//				for (int k = 0; k < excludeCVtrain.size(); k++) {
//					train[k] = excludeCVtrain.get(k);
//				}
//				double[] test = new double[excludeCVtest.size()];
//				for (int k = 0; k < excludeCVtest.size(); k++) {
//					test[k] = excludeCVtest.get(k);
//				}
//
//				if (excludeCVtrain.size() < 2 && excludeCVtest.size() < 2) {
//					cor.get(i).set(j, 0.0);
//					continue;
//				}
//
//				cor.get(i).set(j, new PearsonsCorrelation().correlation(test, train));
//			}
//			System.out.println(i + "/" + test_arr.size());
//		}
//		return cor;
//	}
//
//	// calculates Kendalls AxA
//	public static ArrayList<ArrayList<Double>> calcKendalls(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//		// computing Kendalls Correlation Coefficient one by one
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, new KendallsCorrelation().correlation(x, y));
//			}
//		}
//		return cor;
//	}
//
//	// calculates Kendalls AxB full size
//	public static ArrayList<ArrayList<Double>> calcKendalls(Input input, Instances trainset, Instances testset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//
//		for (int i = 0; i < testset.numInstances(); i++) {
//			double test[] = testset.get(i).toDoubleArray();
//			for (int j = 0; j < trainset.numInstances(); j++) {
//				double train[] = trainset.get(j).toDoubleArray();
//				cor.get(i).set(j, new KendallsCorrelation().correlation(test, train));
//			}
//			System.out.println(i + "/" + testset.size());
//		}
//		return cor;
//	}
//
//	// calculates Kendalls AxB remove zero
//	public static ArrayList<ArrayList<Double>> calcKendallsExclude0(Input input, ArrayList<double[]> train_arr,
//			ArrayList<double[]> test_arr, ArrayList<ArrayList<Double>> cor) throws Exception {
//
//		int attrNum = train_arr.get(0).length;
//
//		for (int i = 0; i < test_arr.size(); i++) {
//			for (int j = 0; j < train_arr.size(); j++) {
//
//				ArrayList<Double> excludeCVtest = new ArrayList<Double>();
//				ArrayList<Double> excludeCVtrain = new ArrayList<Double>();
//
//				for (int k = 0; k < attrNum; k++) {
//					// only compute for non_zero values
//					if (test_arr.get(i)[k] != 0 || train_arr.get(j)[k] != 0) {
//						excludeCVtrain.add(train_arr.get(j)[k]);
//						excludeCVtest.add(test_arr.get(i)[k]);
//					}
//				}
//
//				double[] train = new double[excludeCVtrain.size()];
//				for (int k = 0; k < excludeCVtrain.size(); k++) {
//					train[k] = excludeCVtrain.get(k);
//
//				}
//
//				double[] test = new double[excludeCVtest.size()];
//				for (int k = 0; k < excludeCVtest.size(); k++) {
//					test[k] = excludeCVtest.get(k);
//
//				}
//
//				if (excludeCVtrain.size() == 0 && excludeCVtest.size() == 0) {
//					cor.get(i).set(j, 0.0);
//					continue;
//				}
//
//				double kendalls = new KendallsCorrelation().correlation(test, train);
//				if (kendalls < 0.0) {
//					cor.get(i).set(j, -1.0);
//				} else {
//					cor.get(i).set(j, kendalls);
//				}
//
//			}
//			System.out.println(i + "/" + test_arr.size());
//		}
//		return cor;
//	}
//
//	// calculates Euclidean Distance AxA
//	public static ArrayList<ArrayList<Double>> calcEuclidean(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, new EuclideanDistance().compute(x, y));
//			}
//		}
//		return cor;
//	}
//
//	// calculates Euclidean Distance AxB full size
//	public static ArrayList<ArrayList<Double>> calcEuclidean(Input input, Instances trainset, Instances testset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//		// Euclidean Distance for train and test full size
//		for (int i = 0; i < testset.numInstances(); i++) {
//			double test[] = testset.get(i).toDoubleArray();
//			for (int j = 0; j < trainset.numInstances(); j++) {
//				double train[] = trainset.get(j).toDoubleArray();
//				cor.get(i).set(j, new EuclideanDistance().compute(test, train));
//			}
//			System.out.println(i + "/" + testset.size());
//		}
//		return cor;
//	}
//
//	// calculates Euclidean Distance AxB remove zeros
//	public static ArrayList<ArrayList<Double>> calcEuclideanExclude0(Input input, ArrayList<double[]> train_arr,
//			ArrayList<double[]> test_arr, ArrayList<ArrayList<Double>> cor) throws Exception {
//
//		int attrNum = train_arr.get(0).length;
//		for (int i = 0; i < test_arr.size(); i++) {
//			for (int j = 0; j < train_arr.size(); j++) {
//				ArrayList<Double> excludeCVtrain = new ArrayList<Double>();
//				ArrayList<Double> excludeCVtest = new ArrayList<Double>();
//				for (int k = 0; k < attrNum; k++) {
//					// Make a new vector with values without zero for both train and test
//					if (test_arr.get(i)[k] != 0.0 || train_arr.get(j)[k] != 0.0) {
//						excludeCVtrain.add(train_arr.get(j)[k]);
//						excludeCVtest.add(test_arr.get(i)[k]);
//					}
//				}
//				double[] train = new double[excludeCVtrain.size()];
//				for (int k = 0; k < excludeCVtrain.size(); k++) {
//					train[k] = excludeCVtrain.get(k);
//				}
//				double[] test = new double[excludeCVtest.size()];
//				for (int k = 0; k < excludeCVtest.size(); k++) {
//					test[k] = excludeCVtest.get(k);
//				}
//				cor.get(i).set(j, new EuclideanDistance().compute(test, train));
//			}
//			System.out.println(i + "/" + test_arr.size());
//		}
//		return cor;
//	}
//
//	// calculates Spearmans AxA
//	public static ArrayList<ArrayList<Double>> calcSpearmans(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, new SpearmansCorrelation().correlation(x, y));
//			}
//		}
//		return cor;
//	}
//
//	// calculates Jaccard AxA
//	public static ArrayList<ArrayList<Double>> calcJaccard(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			CharSequence csx = Arrays.toString(x);
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				CharSequence csy = Arrays.toString(y);
//				cor.get(i).set(j, new JaccardSimilarity().apply(csx, csy));
//			}
//		}
//		return cor;
//	}
//
//	// calculates Manhattans Distance AxA
//	public static ArrayList<ArrayList<Double>> calcManhattan(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//		// computing Manhattan Distance one by one
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, new ManhattanDistance().compute(x, y));
//			}
//		}
//		return cor;
//	}
//
//	// calculates Covariance matrix AxA
//	public static ArrayList<ArrayList<Double>> calcCovariance(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//		// computing Covariance one by one
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, new Covariance().covariance(x, y));
//			}
//		}
//		return cor;
//	}
//
//	// calculates cosine similarity AxA
//	public static ArrayList<ArrayList<Double>> calcCosine(Input input, Instances dataset,
//			ArrayList<ArrayList<Double>> cor) throws Exception {
//		// computing Jaccard Similarity Coefficient one by one
//		for (int i = 0; i < dataset.numInstances(); i++) {
//			double[] x = dataset.get(i).toDoubleArray();
//			for (int j = 0; j < dataset.numInstances(); j++) {
//				double[] y = dataset.get(j).toDoubleArray();
//				cor.get(i).set(j, cosineSimilarity(x, y));
//			}
//		}
//
//		return cor;
//	}
//
//	// cosine similarity implementation
//	public static double cosineSimilarity(double[] vectorA, double[] vectorB) {
//		double dotProduct = 0.0;
//		double normA = 0.0;
//		double normB = 0.0;
//		for (int i = 0; i < vectorA.length; i++) {
//			dotProduct += vectorA[i] * vectorB[i];
//			normA += Math.pow(vectorA[i], 2);
//			normB += Math.pow(vectorB[i], 2);
//		}
//		return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
//	}
//
//	static int lcs(char[] X, char[] Y, int m, int n) {
//		int L[][] = new int[m + 1][n + 1];
//
//		for (int i = 0; i <= m; i++) {
//			for (int j = 0; j <= n; j++) {
//				if (i == 0 || j == 0)
//					L[i][j] = 0;
//				else if (X[i - 1] == Y[j - 1])
//					L[i][j] = L[i - 1][j - 1] + 1;
//				else
//					L[i][j] = max(L[i - 1][j], L[i][j - 1]);
//			}
//		}
//		return L[m][n];
//	}
//
//	static int max(int a, int b) {
//		return (a > b) ? a : b;
//	}
//
//	static double max(double a, double b) {
//		return (a > b) ? a : b;
//	}
//
//	static double min(double a, double b) {
//		return (a < b) ? a : b;
//	}
//
//}