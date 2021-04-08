package com.github.thwak.confix.main;

import com.github.thwak.confix.config.Property;
import com.github.thwak.confix.coverage.TestResult;
import com.github.thwak.confix.coverage.Tester;
import com.github.thwak.confix.patch.*;
import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.ChangePoolGenerator;
import com.github.thwak.confix.tree.compiler.Compiler;
import com.github.thwak.confix.util.IOUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.github.thwak.confix.config.Property.*;
import static com.github.thwak.confix.util.IOUtils.*;

public class ConFix {
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));
    public static final int PASS = 0;
    public static final int COMPILE_ERROR = 1;
    public static final int TEST_FAILURE = 2;
    public static final int TEST_TIMEOUT = 3;
    public static final int BREAK_FUNC = 4;
    public static final int TRIGGER_TEST_FAILURE = 5;
    public static final int RELEVANT_TEST_FAILURE = 6;

    public static void main(String[] args) {

        String oldLocKey = "";
        String currentLocKey = null;
        Change prevApplied = null;

        StringBuffer sbLoc = new StringBuffer("Pool,CheckedLines,CheckedLoc,CheckedChange,AppliedChange");
        int totalCompileErrorCount = 0;
        int totalTestFailureCount = 0;
        int totalCandidateCount = 0;
        int candidateCount = 1;
        int compileErrorCount = 0;
        int testFailureCount = 0;
        int locIdx = 0;
        int changeIdx = 0;
        int appliedCount = 0;
        int locChangeCount = 0;
        boolean success = false;
        boolean terminate = false;
        String targetClass = null;
        Patcher patcher = null;

        // ======= STEP 0. Set Properties ======= //
        new Property("confix.properties");
        loadTests();
        loadCoverage();
        checkCoverageValidity();
        long startTime = setTimer();
        Random randomSeed = setRandomSeed(startTime);

        // ======= STEP 1. Generate Pool from SimFin Results ======= //
        setPoolFromSimFinResults();

        // ======= STEP 1-2. Create Patch Strategy ======= //
        PatchStrategy pStrategy;
        pStrategy = StrategyFactory.getPatchStrategy(pStrategyKey, coverage, pool, randomSeed, flMetric,
                    cStrategyKey, sourceDir, compileClassPathEntries);

        pStrategy.finishUpdate();
        IOUtils.storeContent("coveredlines.txt", pStrategy.getLineInfo());

        // ======= STEP 2. Generate Patch Candidates ======= //
        while (candidateCount <= patchCount) {
            int trial = 0;
            int returnCode = -1;

            // ======= STEP 2-1. Select Location to Fix ======= //
            TargetLocation loc = pStrategy.selectLocation();
            targetClass = loc == null ? "" : loc.className;
            currentLocKey = pStrategy.getCurrentLocKey();
            if (!oldLocKey.equals(currentLocKey)) {
                oldLocKey = currentLocKey;
                locIdx++;
                locChangeCount = 0;
            }

            // ======= STEP 2-2. Create Patcher ======= //
            patcher = pStrategy.patcher();
            if (patcher == null) {
                break;
            }

            // ======= STEP 2-3. Select Change Information ======= //
            Change change = pStrategy.selectChange();
            if (change != null) {
                changeIdx++;
                locChangeCount++;
            }
            if (locChangeCount > maxChangeCount) {
                pStrategy.nextLoc();
                continue;
            }

            // TODO: Method Invocation의 SimpleName을 수정할 수 있는 재료 수집
            if (change.type.equals(Change.UPDATE) && loc.node.parent.astNode.getNodeType() == loc.node.parent.astNode.METHOD_INVOCATION) {
                /**
                 * 2021.04.06 TE & YH
                 * 1. loc이 var.method 형태일 때, var 의 타입 확인
                 * 2. 해당 타입의 모든 필드 메소드 이름(String)을 리스트로 만들어 cStrategy 객체에 넣어준다 + 사용할 이름의 인덱스 초기화.
                 * 3. apply가 실행될 때마다 index 증가,
                 * 4. generateNode() 하기 직전에 copied의 value를 list[index]의 값으로 바꾼다.
                 */
            }

            // ======= STEP 2-4. Apply Change Information to Create Patch Candidate =======
            Set<String> candidates = new HashSet<>();
            do {
                success = false;
                PatchInfo info = new PatchInfo(targetClass, change, loc);
                try {
                    returnCode = patcher.apply(loc, change, info);
                    if (DEBUG) {
                        //TE
                        System.out.println("Fix Location");
                        System.out.println(loc);
                        System.out.println("Applied Change");
                        System.out.println(change);
                        System.out.println("Applied new code");
                        System.out.println("Return Code");
                        System.out.println(returnCode);
                    }
                } catch (Exception e) {
                    if (DEBUG) {
                        //TE
                        System.out.println("Fix Location");
                        System.out.println(loc);
                        System.out.println("Applied Change");
                        System.out.println(change);
                        System.out.println("Applied new code");
                        System.out.println("Return Code");
                        System.out.println(returnCode);

                        System.out.println("Change Application Error.");
                        System.out.println("Fix Location");
                        System.out.println(loc);
                        System.out.println("Applied Change");
                        System.out.println(change);
                        e.printStackTrace();
                    }
                    returnCode = Patcher.C_NOT_APPLIED;
                }
                trial++;


                if (returnCode != Patcher.C_NOT_INST && returnCode == Patcher.C_APPLIED) {
                    System.out.println("\nPatch Candidate-" + candidateCount + " is generated.");
                    if (change != null && !change.equals(prevApplied)) {
                        prevApplied = change;
                        appliedCount++;
                    }
                    String editText = PatchUtils.getEditText(info, pool);
                    String newSource = patcher.getNewSource();
                    String candidateFileName = storeCandidate(newSource, editText, targetClass, change, candidateCount);

                    // ======= STEP 2-5. Verify Patch Candidate ======= //
                    int result = verify(candidateFileName);
                    if (result == PASS) {
                        String patchFileName = storePatch(newSource, editText, targetClass, change);
                        System.out.println("A Patch Found! - " + patchFileName);
                        System.out.println("Candidate Number:" + candidateCount);
                        String elapsedTime = PatchUtils.getElapsedTime(System.currentTimeMillis() - startTime);
                        totalCompileErrorCount += compileErrorCount;
                        totalTestFailureCount += testFailureCount;
                        totalCandidateCount += candidateCount;

                        storePatchInfo(totalCandidateCount, totalCompileErrorCount, totalTestFailureCount, elapsedTime,
                                patchFileName, info);

                        success = true;
                        break;
                    } else if (result == COMPILE_ERROR) {
                        compileErrorCount++;
                    } else if (result == TEST_FAILURE || result == TEST_TIMEOUT || result == BREAK_FUNC
                            || result == TRIGGER_TEST_FAILURE || result == RELEVANT_TEST_FAILURE) {
                        testFailureCount++;
                    }

                    candidateCount++;

                } else if (returnCode == Patcher.C_NO_FIXLOC) {
                    break;
                } else if (returnCode == Patcher.C_NO_CHANGE) {
                    break;
                }


                if (isTimeBudgetPassed(startTime)) {
                    terminate = true;
                    System.out.println("Time Budget is passed.");
                    break;
                }

                // TODO: If change is DELETE, UPDATE or NULL, try the change only once.
                /* if(change == null || change.type.equals("delete") )
                  	break;

                 String nodeType = change.node.label.split("::")[0];
                 String locationType = change.location.label.split("::")[0];
                 String locType = loc.node.label.split("::")[0];

                 if(change.type.equals(Change.INSERT) || change.type.equals(Change.DELETE)){
                 	if(nodeType.contains("fixExpression"))
                 		break;
                 }
                 else if(change.type.equals(Change.REPLACE) || change.type.equals(Change.UPDATE)){
                 	if(locType.contains("fixExpression"));
                 		break;
                 	// if(locationType.contains("fixExpression"))
                 	// 	break;
                 }*/


            } while (trial < maxTrials);

            if (terminate || returnCode == Patcher.C_NO_FIXLOC) {
                break;
            }
        }

        if (!success && !terminate) {
            System.out.println("No patch found.");
        }

        System.out.println("Elapsed Time: " + PatchUtils.getElapsedTime(System.currentTimeMillis() - startTime));
        printLocInfo(pStrategy.getCurrentLineIndex() + 1, locIdx, changeIdx, appliedCount, sbLoc);
        System.out.println("Compile Errors:" + compileErrorCount);
        System.out.println("Test Failures:" + testFailureCount);
        IOUtils.storeContent("lines-" + pool.poolName + ".txt", pStrategy.getLocInfo());
        IOUtils.storeContent("locinfo.csv", sbLoc.toString());
    }

    private static void checkCoverageValidity() {
        if (coverage == null || coverage.getNegCoveredClasses().size() == 0) {
            System.out.println("No class/coverage information.");
            return;
        } else if (poolList.size() == 0) {
            System.out.println("No change pool is specified.");
            return;
        }
    }

    private static void printLocInfo(int lines, int locNum, int changeNum, int applied,
                                     StringBuffer sb) {
        System.out.println("Checked Lines:" + lines);
        System.out.println("Checked Fix Locs:" + locNum);
        System.out.println("Checked Changes:" + changeNum);
        System.out.println("Applied Changes:" + applied);
        sb.append("\n");
        sb.append(lines);
        sb.append(",");
        sb.append(locNum);
        sb.append(",");
        sb.append(changeNum);
        sb.append(",");
        sb.append(applied);
    }

    private static long setTimer() {
        long startTime = System.currentTimeMillis();
        System.out.println("Random Seed:" + seed);

        return startTime;
    }

    private static Random setRandomSeed(long startTime) {
        seed = seed == -1 ? new Random(startTime).nextInt(100) : seed;
        Random r = new Random(seed);

        return r;
    }

    private static void setPoolFromSimFinResults() {
        ChangePoolGenerator changePoolGenerator = new ChangePoolGenerator();
        changePoolGenerator.collect(buggyFiles, cleanFiles);
        pool = changePoolGenerator.pool;
        pool.poolName = "SimFinPool";
        pool.maxLoadCount = maxPoolLoad;
        System.out.println("Pool Generation Done.");
    }
    
    private static boolean isTimeBudgetPassed(long startTime) {
        if (timeBudget < 0) {
            return false;
        }
        long expire = startTime + (timeBudget * 60 * 60 * 1000);
        return System.currentTimeMillis() >= expire;
    }

    private static int verify(String patchFileName) {
        // Compile a given patch candidate.
        if (!compileCheck(patchFileName)) {
            return COMPILE_ERROR;
        } else {
            return testCheck();
        }
    }

    public static boolean compileCheck(String patchFileName) {
        if (DEBUG) {
            System.out.print("\n================= 3. Compilation ===================\n");
            System.out.print("\n ================= 3-1. Compile Info ============\n");
            System.out.print("\n 1. patchFileName : " + patchFileName);
            System.out.print("\n 2. tempDir : " + tempDir);
            System.out.print("\n 3. compileClassPath : " + compileClassPath);
            System.out.print("\n 4. Java version : " + version);
            System.out.print("\n 5. JVM : " + jvm);
            System.out.print("\n 6. System JVM : " + System.getProperty("java.version"));
            System.out.print("\n ================================================\n");
        }

        File patchFile = new File(patchFileName);
        Compiler compiler = new Compiler();
        try {
            boolean error = compiler.compile(patchFile, tempDir, compileClassPath, version, version);
            if (error) {
                System.out.println(" - no exception Compile error.");
                return false;
            }
        } catch (Exception e) {
            System.out.println(" - Compile error.");
            return false;
        }
        return true;
    }

    private static void removeBrokenTests(TestResult result) {
        if (result != null) {
            Set<String> falseAlarm = new HashSet<>();
            for (String test : result.failedTests) {
                if (brokenTests.contains(test)) {
                    falseAlarm.add(test);
                }
            }
            result.failedTests.removeAll(falseAlarm);
            result.failCnt = result.failedTests.size();
        }
    }

    public static int testCheck() {
        Tester tester = new Tester(jvm, timeout);
        TestResult result = null;
        String classPath = tempDir + File.pathSeparator + testClassPath;

        if (DEBUG) {
            System.out.print("\n 1. tempDir : " + tempDir);
            System.out.print("\n 2. ClassPath : " + classPath);
            System.out.print("\n 3. JVM : " + jvm);
        }

        try {
            // ============ 4-2 trigger Test results ==============;
            result = tester.runTestsWithJUnitCore(triggerTests, classPath);
            removeBrokenTests(result);
            if (result != null && result.failCnt > 0) {
                System.out.println("Trigger tests - " + result.failCnt + " Tests Failed.");
                if (result.failCnt > numOfTriggers) {
                    return BREAK_FUNC;
                }
                return TRIGGER_TEST_FAILURE;
            } else if (result == null || result.runCnt == 0) {
                System.out.println("An error occurs while running trigger tests - no records.");
                return TEST_TIMEOUT;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("An error occurs while running trigger tests.");
            return TRIGGER_TEST_FAILURE;
        }

        // If passed, run relevant tests.
        if (result != null && result.runCnt > 0 && result.failCnt == 0) {
            try {
                result = tester.runTestsWithJUnitCore(relTests, classPath);
                removeBrokenTests(result);
                if (result != null && result.failCnt > 0) {
                    System.out.println("Relevant tests - " + result.failCnt + " Tests Failed.");
                    return RELEVANT_TEST_FAILURE;
                } else if (result == null || result.runCnt == 0) {
                    System.out.println("An error occurs while running relevant tests. - no records.");
                    return TEST_TIMEOUT;
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("An error occurs while running relevant tests.");
                return RELEVANT_TEST_FAILURE;
            }
        }

        // If passed, run all tests.
        if (result != null && result.runCnt > 0 && result.failCnt == 0) {
            try {
                result = tester.runTestsWithJUnitCore(allTests, classPath);
                removeBrokenTests(result);
                if (result != null && result.failCnt > 0) {
                    System.out.println("All tests - " + result.failCnt + " Tests Failed.");
                    return TEST_FAILURE;
                } else if (result == null || result.runCnt == 0) {
                    System.out.println("An error occurs while running all tests. - no records.");
                    return TEST_TIMEOUT;
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("An error occurs while running all tests.");
                return TEST_FAILURE;
            }
        }

        // If passed, return true.
        if (result != null && result.runCnt > 0 && result.failCnt == 0) {
            return PASS;
        } else {
            return TEST_FAILURE;
        }
    }
}

