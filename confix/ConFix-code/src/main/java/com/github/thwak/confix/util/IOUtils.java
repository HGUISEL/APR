package com.github.thwak.confix.util;

import com.github.thwak.confix.patch.models.PatchInfo;
import com.github.thwak.confix.pool.changes.Change;
import com.github.thwak.confix.tester.coverage.CoverageManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.github.thwak.confix.config.Property.*;

public class IOUtils {

    public static String readFile(File f) {
        StringBuffer sb = new StringBuffer();
        BufferedReader br = null;
        FileReader fr = null;
        try {
            fr = new FileReader(f);
            br = new BufferedReader(fr);
            char[] cbuf = new char[500];
            int len = 0;
            while ((len = br.read(cbuf)) > -1) {
                sb.append(cbuf, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    public static String readFile(String filePath) {
        return readFile(new File(filePath));
    }

    public static void storeDataToFile(Collection<? extends Object> data, String fileName) throws IOException {
        File outputFile = new File(fileName);
        FileOutputStream fos = new FileOutputStream(outputFile);
        PrintWriter pw = new PrintWriter(fos);
        for (Object obj : data) {
            pw.println(obj);
        }
        pw.flush();
        pw.close();
    }

    public static void storeContent(String filePath, String content) {
        storeContent(new File(filePath), content, false);
    }

    public static void storeContent(String filePath, String content, boolean append) {
        storeContent(new File(filePath), content, append);
    }

    public static void storeContent(File f, String content, boolean append) {
        FileOutputStream fos = null;
        PrintWriter pw = null;
        try {
            fos = new FileOutputStream(f, append);
            pw = new PrintWriter(fos);
            pw.print(content);
            pw.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (pw != null) {
                    pw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static void storeObject(String filePath, Object obj) {
        storeObject(new File(filePath), obj);
    }

    public static void storeObject(File f, Object obj) {
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(f);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (oos != null) {
                    oos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Object readObject(String filePath) {
        return readObject(new File(filePath));
    }

    public static Object readObject(File f) {
        FileInputStream fis = null;
        ObjectInputStream is = null;
        Object obj = null;
        try {
            fis = new FileInputStream(f);
            is = new ObjectInputStream(fis);
            obj = is.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return obj;
    }

    public static void delete(File file) {
        try {
            Files.walkFileTree(file.toPath(), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        return FileVisitResult.TERMINATE;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void storePatchInfo(int totalCandidateNum, int totalCompileError, int totalTestFailure,
                                      String elapsedTime, String patchFileName, PatchInfo info) {
        StringBuffer sb = new StringBuffer();
        sb.append("Seed:");
        sb.append(seed);
        sb.append("|Pool:");
        sb.append("beautyPool");
        sb.append("|PatchNum:");
        sb.append(totalCandidateNum);
        sb.append("|Time:");
        sb.append(elapsedTime.trim());
        sb.append("|CompileError:");
        sb.append(totalCompileError);
        sb.append("|TestFailure:");
        sb.append(totalTestFailure);
        sb.append("|Concretize:");
        sb.append(info.getConcretize());
        sb.append("\n");
        sb.append(patchFileName);
        sb.append("\n");
        IOUtils.storeContent("patch_info", sb.toString(), true);
    }

    public static String storePatch(String newSource, String patch, String targetClass, Change change) {
        int lastDotIndex = targetClass.lastIndexOf('.');
        String packageName = targetClass.substring(0, lastDotIndex);
        String fileName = targetClass.substring(lastDotIndex + 1) + ".java";
        File dir = new File(patchDir);
        File[] dirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() && f.getName().matches("[0-9]+");
            }
        });
        int patchId = dirs == null ? 0 : dirs.length;
        String patchPath = patchDir + File.separator + patchId + File.separator;
        String packagePath = patchPath + packageName.replaceAll("\\.", File.separator + File.separator);
        File packageDir = new File(packagePath);
        if (!packageDir.exists()) {
            packageDir.mkdirs();
        }
        String filePath = Paths.get(packagePath + File.separator + fileName).toString();
        IOUtils.storeContent(filePath, newSource);
        IOUtils.storeContent(patchPath + "edit", patch);
        IOUtils.storeObject(patchPath + "change.obj", change);

        return filePath;
    }

    public static String storeCandidate(String newSource, String patch, String targetClass, Change change, int candidateNum) {
        int lastDotIndex = targetClass.lastIndexOf('.');
        String packageName = targetClass.substring(0, lastDotIndex);
        String fileName = targetClass.substring(lastDotIndex + 1) + ".java";
        //String candidatePath = candidateDir + File.separator + "candidate_"+candidateNum + File.separator;
        String candidatePath = candidateDir + File.separator + "candidate" + File.separator;
        String packagePath = candidatePath + packageName.replaceAll("\\.", File.separator + File.separator);
        File dir = new File(packagePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filePath = Paths.get(packagePath + File.separator + fileName).toString();
        IOUtils.storeContent(filePath, newSource);
        IOUtils.storeContent(candidatePath + "edit", patch);
        IOUtils.storeObject(candidatePath + "change.obj", change);

        return filePath;
    }

    public static void loadCoverage() {
        System.out.print("Loading Coverage Information....");
        coverage = (CoverageManager) IOUtils.readObject("coverage-info.obj");
        System.out.println("Done.");
    }

    public static void loadTests() {
        String trigger = IOUtils.readFile("tests.trigger");
        String relevant = IOUtils.readFile("tests.relevant");
        String all = IOUtils.readFile("tests.all");
        Set<String> testSet = new HashSet<>();
        String[] tests = trigger.split("\n");
        numOfTriggers = tests.length;
        for (String test : tests) {
            // Get the class name only for trigger tests.
            if (!test.startsWith("#")) {
                testSet.add(test.split("::")[0]);
            }
        }
        triggerTests.addAll(testSet);
        relTests.addAll(Arrays.asList(relevant.split("\n")));
        allTests.addAll(Arrays.asList(all.split("\n")));
        File f = new File("tests.broken");
        if (f.exists()) {
            String broken = IOUtils.readFile("tests.broken");
            tests = broken.split("\n");
            for (String t : tests) {
                brokenTests.add(t.replace("::", "#"));
            }
        }
    }
}
