package com.github.thwak.confix.patch.models;

import com.github.thwak.confix.pool.changes.Change;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PatchInfo {

    public TargetLocation loc;
    public Change change;
    public String className;
    public Set<String> cMethods;
    public List<RepairAction> repairs;

    public PatchInfo(String targetClass, Change change, TargetLocation loc) {
		className = targetClass;
        this.loc = loc;
        this.change = change;
		repairs = new ArrayList<>();
		cMethods = new HashSet<>();
    }

    public String getConcretize() {
        StringBuffer sb = new StringBuffer();
        for (String str : cMethods) {
            sb.append(",");
            sb.append(str);
        }
        return sb.substring(1);
    }
}
