package com.github.thwak.confix.pool.contexts;

import com.github.thwak.confix.pool.changes.Change;

import java.io.Serializable;
import java.util.*;

public class ContextInfo implements Serializable {

    private static final long serialVersionUID = -3829360414319735480L;
    protected int freq = 0;
    protected List<Integer> changes;
    protected Map<Integer, Integer> changeFreq;
    protected Map<String, Integer> typeCount;
    protected boolean sorted;

    public ContextInfo() {
        changes = new ArrayList<>();
        changeFreq = new HashMap<>();
        typeCount = new HashMap<>();
        sorted = false;
    }

    public int getChangeFreq(int id) {
        return changeFreq.containsKey(id) ? changeFreq.get(id) : 0;
    }

    public void addChange(int id, Change c, int freq) {
        this.freq += freq;
        if (changeFreq.containsKey(id)) {
            changeFreq.put(id, changeFreq.get(id) + freq);
        } else {
            changeFreq.put(id, freq);
            changes.add(id);
        }
        if (!typeCount.containsKey(c.type)) {
            typeCount.put(c.type, 1);
        } else {
            typeCount.put(c.type, typeCount.get(c.type) + 1);
        }
        sorted = false;
    }

    public List<Integer> getChanges() {
        if (!sorted) {
            sort();
        }
        return changes;
    }

    public void sort() {
        Collections.sort(changes, new Comparator<Integer>() {
            @Override
            public int compare(Integer id1, Integer id2) {
                return Integer.compare(changeFreq.get(id2), changeFreq.get(id1));
            }
        });
        sorted = true;
    }
}
