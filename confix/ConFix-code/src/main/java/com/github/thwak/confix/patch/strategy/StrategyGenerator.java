package com.github.thwak.confix.patch.strategy;

import com.github.thwak.confix.pool.changes.ChangePool;
import com.github.thwak.confix.tester.coverage.CoverageInfo;
import com.github.thwak.confix.tester.coverage.CoverageManager;

import java.util.Random;

import static com.github.thwak.confix.config.Property.pFaultyClass;
import static com.github.thwak.confix.config.Property.pFaultyLine;

public class StrategyGenerator {
    public static PatchStrategy getPatchStrategy(String key, CoverageManager coverage, ChangePool pool, Random r,
                                                 String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries) {
        PatchStrategy strategy = null;
        key = key.toLowerCase();

        switch (key) {
            case "noctx":
                if (flMetric.equals("perfect")) {
                    strategy = new PatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey,
                            sourceDir, compileClassPathEntries, pFaultyClass, pFaultyLine);
                } else {
                    strategy = new PatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey,
                            sourceDir, compileClassPathEntries);
                }
                break;
            default:
                strategy = new PatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey, sourceDir,
                        compileClassPathEntries);
        }
        return strategy;
    }

    public static ConcretizationStrategy getConcretizationStrategy(String key, CoverageManager coverage,
                                                                   String className, String srcDir, Random r) {
        ConcretizationStrategy strategy = null;
        CoverageInfo info = coverage.get(className);
        String packageName = getPackageName(className);
        key = key.toLowerCase();
        switch (key) {
            case "tc":
            default:
                strategy = new ConcretizationStrategy(r); // Default Type-compatible strategy
        }
        return strategy;
    }

    private static String getPackageName(String className) {
        if (className == null) {
            return "";
        }
        int index = className.lastIndexOf('.');
        return index < 0 ? "" : className.substring(0, index);
    }

}
