package com.github.thwak.confix.diff;

import org.eclipse.jdt.core.dom.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Node implements Serializable {

    private static final long serialVersionUID = 8020769106117625499L;
    public static final String DELIM = "::";
    public static final int K_CONSTRUCTOR = 0;
    public static final int K_VARIABLE = IBinding.VARIABLE;
    public static final int K_METHOD = IBinding.METHOD;
    public static final int K_TYPE = IBinding.TYPE;

    public int id;
    public String label;
    public transient ASTNode astNode;
    public int type;
    public List<Node> children;
    public Node parent;
    public int startLine;
    public int endLine;
    public int startPos;
    public int length;
    public int posInParent;
    public String value;
    public transient String hashString;
    public boolean isStatement = false;
    public int kind = -1;
    public boolean normalized = false;
    public StructuralPropertyDesc desc;
    public boolean isMatched = true;

    public Node(String label) {
        this(label, -1, "");
    }

    public Node(String label, int type) {
        this(label, type, "");
    }

    public Node(String label, int type, String value) {
		id = -1;
        this.label = label;
        this.type = type;
        this.value = value;
		astNode = null;
		children = new ArrayList<Node>();
		parent = null;
		startLine = 0;
		endLine = 0;
		startPos = 0;
		length = 0;
		posInParent = 0;
		hashString = null;
		isStatement = false;
		desc = null;
		isMatched = false;
    }

    public Node(String label, ASTNode node, String value) {
        this.label = label;
		astNode = node;
		type = node.getNodeType();
		children = new ArrayList<Node>();
		parent = null;
        this.value = value;
		startPos = node.getStartPosition();
		length = node.getLength();
		posInParent = 0;
		hashString = null;
        if (node.getRoot() instanceof CompilationUnit) {
            CompilationUnit cu = (CompilationUnit) node.getRoot();
			startLine = cu.getLineNumber(startPos);
			endLine = cu.getLineNumber(startPos + length);
        } else {
			startLine = 0;
			endLine = 0;
        }
        if (node instanceof Statement
                || (node.getParent() != null && node.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)) {
			isStatement = true;
        }
        if (node instanceof Name) {
            IBinding b = ((Name) node).resolveBinding();
            if (b == null) {
                if (node.getParent() instanceof MethodDeclaration) {
                    MethodDeclaration md = (MethodDeclaration) node.getParent();
                    if (node.getLocationInParent().equals(MethodDeclaration.NAME_PROPERTY)) {
						kind = md.isConstructor() ? K_CONSTRUCTOR : K_METHOD;
                    } else {
						kind = IBinding.VARIABLE;
                    }
                } else if (node.getParent() instanceof MethodInvocation) {
                    MethodInvocation mi = (MethodInvocation) node.getParent();
					kind = mi.getName() == node ? K_METHOD : IBinding.VARIABLE;
                } else if (node.getParent() instanceof MemberValuePair) {
					kind = IBinding.MEMBER_VALUE_PAIR;
                } else if (node.getParent() instanceof Annotation) {
					kind = IBinding.ANNOTATION;
                } else if (node.getParent() instanceof ImportDeclaration) {
					kind = IBinding.TYPE;
                } else if (node.getParent() instanceof PackageDeclaration) {
					kind = IBinding.PACKAGE;
                } else {
					kind = IBinding.VARIABLE;
                }
            } else {
				kind = b.getKind();
            }
        } else if (node instanceof Type) {
			kind = IBinding.TYPE;
        }
        if (node.getParent() != null && node.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
            ASTNode parent = node.getParent();
            StructuralPropertyDescriptor spd = parent.getLocationInParent();
			desc = new StructuralPropertyDesc(spd, ASTNode.nodeClassForType(parent.getParent().getNodeType()));
        } else {
            StructuralPropertyDescriptor spd = node.getLocationInParent();
			desc = spd == null ? null : new StructuralPropertyDesc(spd, ASTNode.nodeClassForType(node.getParent().getNodeType()));
        }

    }

    public boolean isRoot() {
        return parent == null;
    }

    public void addChild(Node child) {
        if (child != null) {
			children.add(child);
            child.parent = this;
            child.posInParent = children.size() - 1;
        }
    }

    public String computeLabel() {
		label = astNode != null ? astNode.getClass().getSimpleName() + Node.DELIM + value
                : ASTNode.nodeClassForType(type).getSimpleName() + Node.DELIM + value;
        return label;
    }

    public Node getLeft() {
        return parent != null && posInParent > 0 ? parent.children.get(posInParent - 1) : null;
    }

    public Node getRight() {
        return parent != null && posInParent < parent.children.size() - 1 ? parent.children.get(posInParent + 1) : null;
    }

    public Node copy() {
        Node n = new Node(label, type, value);
        n.id = id;
        n.astNode = astNode;
        n.children = new ArrayList<Node>();
        n.parent = null;
        n.startLine = startLine;
        n.endLine = endLine;
        n.startPos = startPos;
        n.length = length;
        n.posInParent = posInParent;
        n.hashString = hashString;
        n.kind = kind;
        n.isStatement = isStatement;
        n.desc = desc != null ? new StructuralPropertyDesc(desc.id, desc.type, desc.className) : null;
        n.isMatched = isMatched;
        n.normalized = normalized;
        return n;
    }
}
