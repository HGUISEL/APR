package com.github.thwak.confix.pool;

import java.io.Serializable;

public class Variable implements Serializable {
    private static final long serialVersionUID = 2209664254767076382L;
    public String name;
    public VariableType type;
    public boolean isDeclaration = false;
    public boolean isFieldAccess = false;
    public VariableType declType = null;


    public Variable(String name, VariableType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public int hashCode() {
        String hash = name + "::" + type.name;
        return hash.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Variable) {
            Variable v = (Variable) obj;
            return name.equals(v.name) && type.isSameType(v.type);
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
