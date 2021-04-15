package com.github.thwak.confix.patch;

import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.coverage.CoveredLine;
import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.pool.ContextIdentifier;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;
import com.github.thwak.confix.util.IndexMap;

import java.util.*;

public class PatchStrategy {

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));
    protected Random r;
    protected CoverageManager manager;
    protected ChangePool pool;
    protected ContextIdentifier collector;
    protected List<LocEntry> locations;
    protected IndexMap<CoveredLine> coveredLines;
    protected Map<Integer, List<LocEntry>> lineLocMap;
    protected int currLocIndex = 0;
    protected int currLineIndex = -1;
    protected String cStrategyKey;
    protected String flMetric;
    protected Map<String, Patcher> patcherMap;
    protected String sourceDir;
    protected String[] compileClassPathEntries;
    protected int fixLocCount = 0;
    protected StringBuffer sbLoc = new StringBuffer("LocKind$$Loc$$Class#Line:Freq:Score");

    // Normal FL
    public PatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r,
                         String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries) {
        this.r = r;
        this.manager = manager;
        this.pool = pool;
        this.collector = collector;
        locations = new ArrayList<>();
        coveredLines = new IndexMap<>();
        lineLocMap = new HashMap<>();
        patcherMap = new HashMap<>();
        this.flMetric = flMetric;
        this.cStrategyKey = cStrategyKey;
        this.sourceDir = sourceDir;
        this.compileClassPathEntries = compileClassPathEntries;
        prioritizeCoveredLines();
    }

    // Perfect FL
    public PatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r,
                         String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries,
                         String pFaultyClass, int pFaultyLine) {
        this.r = r;
        this.manager = manager;
        this.pool = pool;
        this.collector = collector;
        locations = new ArrayList<>();
        coveredLines = new IndexMap<>();
        lineLocMap = new HashMap<>();
        patcherMap = new HashMap<>();
        this.flMetric = flMetric;
        this.cStrategyKey = cStrategyKey;
        this.sourceDir = sourceDir;
        this.compileClassPathEntries = compileClassPathEntries;
        perfectFlTargetLine(pFaultyClass, pFaultyLine);
    }

    protected void perfectFlTargetLine(String pFaultyClass, int pFaultyLine) {
        CoveredLine coveredline = new CoveredLine(pFaultyClass, pFaultyLine);
        System.out.println("Covered line added with class name: " + pFaultyClass);
        coveredLines.add(coveredline);
    }

    protected void prioritizeCoveredLines() {
        List<CoveredLine> lines = manager.computeScore(flMetric);
        for (CoveredLine cl : lines) {
            if (Double.compare(cl.score, 0.0000d) > 0) {
                coveredLines.add(cl);
            }
        }
    }

    public String getLineInfo() {
        StringBuffer sb = new StringBuffer();
        for (CoveredLine cl : coveredLines.values()) {
            sb.append(cl.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public int getCurrentLineIndex() {
        return currLineIndex;
    }

    public ContextIdentifier collector() {
        return collector;
    }

    public boolean inRange(String className, Node n) {
        return coveredLines.contains(new CoveredLine(className, n.startLine));
    }

    public void updateLocations(String className, Node root, FixLocationIdentifier identifier) {
        List<TargetLocation> fixLocs = new ArrayList<>();
        identifier.findLocations(className, root, fixLocs);
        fixLocCount += fixLocs.size();
        for (TargetLocation loc : fixLocs) {
            CoveredLine cl = new CoveredLine(className, loc.node.startLine);
            int index = coveredLines.getIndex(cl);
            if (!lineLocMap.containsKey(index)) {
                lineLocMap.put(index, new ArrayList<LocEntry>());
            }
            lineLocMap.get(index).add(new LocEntry(loc, coveredLines.get(index).score));
        }
    }

    public void nextLoc() {
        currLocIndex++;
    }

    public TargetLocation selectLocation() {
        if (currLocIndex < locations.size()) {
            LocEntry e = locations.get(currLocIndex);
            // TE
            if (e.changeIds == null) {
                e.changeIds = findCandidateChanges(e.loc);
                appendLoc(e);
            }
            return e.loc;
        } else {
            if (++currLineIndex < coveredLines.size()) {
                locations.clear();
                CoveredLine cl = coveredLines.get(currLineIndex);
                if (!patcherMap.containsKey(cl.className)) {
                    System.out.println("Loading Class - " + cl.className);
                    String source = PatchUtils.loadSource(sourceDir, cl.className);
                    ConcretizationStrategy cStrategy = StrategyFactory.getConcretizationStrategy(cStrategyKey, manager,
                            cl.className, sourceDir, r);
                    Patcher patcher = new Patcher(cl.className, source, compileClassPathEntries,
                            new String[]{sourceDir}, this, cStrategy);
                    patcherMap.put(cl.className, patcher);
                }
                if (lineLocMap.containsKey(currLineIndex)) {
                    locations.addAll(lineLocMap.get(currLineIndex));
                }
                currLocIndex = 0;
                return selectLocation();
            }
            return null;
        }
    }

    public List<Integer> findCandidateChanges(TargetLocation loc) {
        return findCandidateChanges(loc, false);
    }

    public List<Integer> findCandidateChanges(TargetLocation loc, boolean checkOnly) {
        List<Integer> candidates = new ArrayList<>();
        Iterator<Integer> it = pool.changeIterator();
        while (it.hasNext()) {
            int id = it.next();
            Change c = pool.getChange(id);
            if (loc.kind != TargetLocation.DEFAULT && checkDescriptor(loc, c) && loc.isCompatible(c)) {
                candidates.add(id);
            } else if (!c.type.equals(Change.INSERT) && loc.kind == TargetLocation.DEFAULT) {
                if (c.node.hashString == null) {
                    c.node.hashString = TreeUtils.getTypeHash(c.node);
                }
                if (loc.node.hashString == null) {
                    loc.node.hashString = TreeUtils.getTypeHash(loc.node);
                }
                switch (c.type) {
                    case Change.UPDATE:
                        if (c.node.isStatement) {
                            candidates.add(id);
                        } else if (c.node.kind == loc.node.kind) {
                            if (c.node.normalized || loc.isCompatible(c)) {
                                candidates.add(id);
                            }
                        }
                        break;
                    case Change.REPLACE:
                        if (c.node.isStatement || loc.isCompatible(c)) {
                            candidates.add(id);
                        }
                        break;
                    case Change.DELETE:
                    case Change.MOVE:
                        if (c.node.hashString.equals(loc.node.hashString) && c.node.kind == loc.node.kind) {
                            candidates.add(id);
                        }
                        break;
                }
            }
            if (checkOnly && candidates.size() > 0) {
                return candidates;
            }
        }
        return candidates;
    }

    protected boolean checkDescriptor(TargetLocation loc, Change c) {
        return c.type.equals(Change.INSERT);
    }


    public Change selectChange() {
        if (currLocIndex < locations.size()) {
            LocEntry e = locations.get(currLocIndex);
            Change c = e.changeIds != null && e.changeIds.size() > 0 ? pool.getChange(e.changeIds.remove(0)) : null;
            if (e.changeIds.size() == 0) {
                nextLoc();
            }
            return c;
        }
        return null;
    }

    public String getCurrentLocKey() {
        return currLineIndex + ":" + currLocIndex;
    }

    public String getCurrentClass() {
        return currLineIndex >= 0 && currLineIndex < coveredLines.size() ? coveredLines.get(currLineIndex).className
                : "";
    }

    public Patcher patcher() {
        return patcherMap.get(getCurrentClass());
    }

    protected void appendLoc(LocEntry e) {
        sbLoc.append("\n");
        sbLoc.append(e.loc.getTypeName());
        sbLoc.append("$$");
        sbLoc.append(e.loc.node.label);
        sbLoc.append("$$");
        sbLoc.append(e.loc.className);
        sbLoc.append("#");
        sbLoc.append(e.loc.node.startLine);
        sbLoc.append(":");
        sbLoc.append(e.freq);
        sbLoc.append(":");
        sbLoc.append(String.format("%1.4f", e.score));
    }

    public String getLocInfo() {
        return sbLoc.toString();
    }

    protected static class LocEntry implements Comparable<LocEntry> {
        public TargetLocation loc;
        public List<Integer> changeIds;
        public int freq;
        public double score;

        public LocEntry(TargetLocation loc, double score) {
            this(loc, 0, score);
        }

        public LocEntry(TargetLocation loc, int freq, double score) {
            this.loc = loc;
            this.freq = freq;
            this.score = score;
            changeIds = null;
        }

        @Override
        public int compareTo(LocEntry e) {
            return Integer.compare(e.freq, freq);
        }
    }

    public void finishUpdate() {
        // Do nothing for the baseline.
    }
}
