package com.github.thwak.confix.config;

import com.github.thwak.confix.tester.coverage.CoverageManager;
import com.github.thwak.confix.patch.utils.PatchUtils;
import com.github.thwak.confix.pool.changes.ChangePool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class Property {

    public static String testClassPath;
    public static String compileClassPath;
    public static String[] testClassPathEntries;
    public static String[] compileClassPathEntries;
    public static String libClassPath;
    public static List<String> modifiedClasses = new ArrayList<>();
    public static CoverageManager coverage;
    public static String sourceDir;
    public static String targetDir;
    public static String tempDir;
    public static List<String> poolList;
    public static ChangePool pool;
    public static int patchCount = 20;
    public static int maxTrials = 10;
    public static String candidateDir = "candidates";
    public static String patchDir = "patches";
    public static String jvm;
    public static String version;
    public static long timeout;
    public static List<String> triggerTests = new ArrayList<>();
    public static List<String> relTests = new ArrayList<>();
    public static List<String> allTests = new ArrayList<>();
    public static Set<String> brokenTests = new HashSet<>();
    public static long seed;
    public static int numOfTriggers;
    public static long timeBudget;
    public static String pStrategyKey;
    public static String cStrategyKey;
    public static String flMetric;
    public static int maxPoolLoad;
    public static int maxChangeCount;
    public static int simfinPatchNum = 10;
    public static List<File> cleanFiles = new ArrayList<File>(simfinPatchNum + 1);
    public static List<File> buggyFiles = new ArrayList<File>(simfinPatchNum + 1);
    public static String projectName;
    public static String bugId;

    public static int pFaultyLine;
    public static String pFaultyClass;

    public Property(String fileName) {
        Properties props = new Properties();
        File f = new File(fileName);
        try {
            FileInputStream fis = new FileInputStream(f);
            props.load(fis);
            fis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        libClassPath = PatchUtils.getStringProperty(props, "cp.lib", "");
        testClassPath = PatchUtils.getStringProperty(props, "cp.test", "");
        compileClassPath = PatchUtils.getStringProperty(props, "cp.compile", "");
        String priority = PatchUtils.getStringProperty(props, "cp.test.priority", "local");
        if (libClassPath.length() > 0) {
            if ("cfix".equals(priority)) {
                testClassPath = libClassPath + File.pathSeparatorChar + testClassPath;
            } else {
                testClassPath = testClassPath + File.pathSeparatorChar + libClassPath;
            }
        }
        testClassPathEntries = testClassPath.split(File.pathSeparator);
        compileClassPathEntries = compileClassPath.split(File.pathSeparator);
        sourceDir = PatchUtils.getStringProperty(props, "src.dir", "src/main/java");
        targetDir = PatchUtils.getStringProperty(props, "target.dir", "target/classes");
        modifiedClasses = PatchUtils.getListProperty(props, "classes.modified", ",");
        poolList = PatchUtils.getListProperty(props, "pool.path", ",");
        jvm = PatchUtils.getStringProperty(props, "jvm", "/usr/bin/java");
        version = PatchUtils.getStringProperty(props, "version", "1.7");
        timeout = Long.parseLong(PatchUtils.getStringProperty(props, "timeout", "10")) * 1000;
        patchCount = Integer.parseInt(PatchUtils.getStringProperty(props, "patch.count", "20"));
        maxTrials = Integer.parseInt(PatchUtils.getStringProperty(props, "max.trials", "10"));
        maxChangeCount = Integer.parseInt(PatchUtils.getStringProperty(props, "max.change.count", "25"));
        maxPoolLoad = Integer.parseInt(PatchUtils.getStringProperty(props, "max.pool.load", "1000"));
        seed = Long.parseLong(PatchUtils.getStringProperty(props, "seed", "-1"));
        tempDir = new File("tmp").getAbsolutePath();
        timeBudget = Long.parseLong(PatchUtils.getStringProperty(props, "time.budget", "-1"));
        pStrategyKey = PatchUtils.getStringProperty(props, "patch.strategy", "flfreq");
        cStrategyKey = PatchUtils.getStringProperty(props, "concretize.strategy", "tc");
        flMetric = PatchUtils.getStringProperty(props, "fl.metric", "ochiai");
        projectName = PatchUtils.getStringProperty(props, "projectName", "Closure");
        bugId = PatchUtils.getStringProperty(props, "bugId", "3");
        pFaultyLine = Integer.parseInt(PatchUtils.getStringProperty(props, "pFaultyLine", "1"));
        pFaultyClass = PatchUtils.getStringProperty(props, "pFaultyClass", "");
        // System.out.println("pFaultyClass as it is: " + pFaultyClass);
        pFaultyClass = pFaultyClass.replace(sourceDir.replaceAll("/", ".") + ".", "");
        // System.out.println("pFaultyClass after replacing is: " + pFaultyClass);
        createFileLists(projectName, bugId);
    }

    public static void createFileLists(String projectName, String bugId) {
        File dir = new File("../../pool/prepare_pool_source");
        //File dir = new File("/home/goodtaeeun/APR_Projects/APR/pool/las/data");
        File[] directoryListing = dir.listFiles();
        //System.out.println("File list size: "+directoryListing.length()) ;

        for (int i = 0; i < 11; i++) {
            cleanFiles.add(null);
            buggyFiles.add(null);
        }

        if (directoryListing != null) {
            for (File child : directoryListing) {
                int start = child.getName().indexOf('-') + 1;
                int end = child.getName().lastIndexOf('_');
                int number = Integer.parseInt(child.getName().substring(start, end));
                try {
                    //System.out.println("Child path: " + child.getCanonicalPath()) ;
                    String[] childPath = child.getCanonicalPath().split("source/"); // "data/"" 뒤에는 파일 이름이 붙는다.
                    if (childPath[1].indexOf("new") > 0) {
                        cleanFiles.set(number, child);
                    } else {
                        buggyFiles.set(number, child);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }

}