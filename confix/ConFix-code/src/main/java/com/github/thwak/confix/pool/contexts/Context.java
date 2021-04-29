package com.github.thwak.confix.pool.contexts;

import com.github.thwak.confix.diff.DiffUtils;

import java.io.Serializable;

public class Context implements Serializable {
    private static final long serialVersionUID = -1783212689497010361L;
    public String hashString;
    public String hash;
    public int hashCode;

    public Context(String hashString) {
        this(hashString, DiffUtils.computeSHA256Hash(hashString));
    }

    public Context(String hashString, String hash) {
        this.hashString = hashString;
        this.hash = hash;
		hashCode = hash.hashCode();
    }

    public Context() {
        this("");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Context) {
            return hash.equals(((Context) obj).hash);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return hashString;
    }
}
