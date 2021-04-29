package com.github.thwak.confix.patch;

import com.github.thwak.confix.diff.CodeVisitor;
import com.github.thwak.confix.diff.DiffUtils;
import com.github.thwak.confix.diff.Node;
import com.github.thwak.confix.diff.Parser;
import com.github.thwak.confix.patch.models.FixLocationIdentifier;
import com.github.thwak.confix.patch.models.PatchInfo;
import com.github.thwak.confix.patch.models.RepairAction;
import com.github.thwak.confix.patch.models.TargetLocation;
import com.github.thwak.confix.patch.strategy.ConcretizationStrategy;
import com.github.thwak.confix.patch.strategy.PatchStrategy;
import com.github.thwak.confix.pool.changes.Change;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PatchApplier {

    public static int C_NOT_APPLIED = 0;
    public static int C_APPLIED = 1;
    public static int C_NOT_INST = 2;
    public static int C_NO_FIXLOC = 3;
    public static int C_NO_CHANGE = 4;

    public String className;
    public Document doc;
    private CompilationUnit cu;
    private ASTRewrite rewrite;
    private final PatchStrategy pStrategy;
    private final ConcretizationStrategy cStrategy;
    private final FixLocationIdentifier identifier;
    public String[] classPath;
    public String[] sourcePath;

    public PatchApplier(String className, String source, String[] classPath, String[] sourcePath, PatchStrategy pStrategy,
                        ConcretizationStrategy cStrategy) {
        this(className, source, classPath, sourcePath, pStrategy, cStrategy,
                new FixLocationIdentifier(pStrategy, cStrategy));
    }

    public PatchApplier(String className, String source, String[] classPath, String[] sourcePath, PatchStrategy pStrategy,
                        ConcretizationStrategy cStrategy, FixLocationIdentifier identifier) {
        this.className = className;
        doc = new Document(source);
        this.pStrategy = pStrategy;
        this.cStrategy = cStrategy;
        this.identifier = identifier;
        this.classPath = classPath;
        this.sourcePath = sourcePath;
        updateDocInfo();
    }

    private void updateDocInfo() {
        // TODO: need to pass source/class path for multiple changes.
        Parser parser = new Parser(classPath, sourcePath);
        cu = parser.parse(doc.get());
        Node root = new Node("root");
        rewrite = ASTRewrite.create(cu.getAST());
        CodeVisitor visitor = new CodeVisitor(root);
        cu.accept(visitor);
        cStrategy.collectGlobalMaterials(parser, className, root, cu);
        pStrategy.updateLocations(className, root, identifier);
    }

    public String getNewSource() {
        Document copy = new Document(doc.get());
        if (rewrite != null) {
            try {
                TextEdit edit = rewrite.rewriteAST(copy, null);
                edit.apply(copy);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (MalformedTreeException e) {
                e.printStackTrace();
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
        rewrite = ASTRewrite.create(cu.getAST());
        return copy.get();
    }

    public int apply(TargetLocation loc, Change change, PatchInfo info) {
        if (loc == null) {
            return C_NO_FIXLOC;
        }
        if (change == null) {
            return C_NO_CHANGE;
        }

        int returnCode = C_NOT_APPLIED;
        switch (change.type) {
            case Change.DELETE:
                String editHash = DiffUtils.getTypeHash(change.node);
                String locHash = DiffUtils.getTypeHash(loc.node);
                if (editHash.equals(locHash)) {
                    RepairAction repair = new RepairAction(Change.DELETE, loc, getCode(loc.node), "", change);
                    delete(loc.node);
                    info.repairs.add(repair);
                    returnCode = C_APPLIED;
                }
                break;
            case Change.INSERT:
                if (cStrategy.instCheck(change, loc) && change.node.desc != null) {
                    StructuralPropertyDescriptor descriptor = loc.desc;
                    ASTNode parent = loc.node.astNode.getParent();
                    try {
                        if (parent == null || !descriptor.isChildListProperty()
                                && parent.getStructuralProperty(descriptor) != null
                                && parent.getNodeType() != ASTNode.INFIX_EXPRESSION
                                && !(parent.getNodeType() == ASTNode.IF_STATEMENT
                                && descriptor.getId().equals(IfStatement.THEN_STATEMENT_PROPERTY.getId()))
                                && !(parent.getNodeType() == ASTNode.METHOD_INVOCATION
                                && descriptor.getId().equals(MethodInvocation.NAME_PROPERTY.getId()))) {
                            return C_NOT_APPLIED;
                        }
                    } catch (RuntimeException e) {
                        return C_NOT_APPLIED;
                    }
                    ASTNode astNode = cStrategy.instantiate(change, loc, info);
                    if (astNode == null) {
                        return C_NOT_INST;
                    }
                    insert(loc, astNode, descriptor);
                    RepairAction repair = new RepairAction(Change.INSERT, loc, "", astNode.toString(), change);
                    info.repairs.add(repair);
                    returnCode = C_APPLIED;
                }
                break;
            case Change.UPDATE:
                if (cStrategy.instCheck(change, loc)) {
                    ASTNode astNode = cStrategy.instantiate(change, loc, info);
                    if (astNode == null) {
                        return C_NOT_INST;
                    }
                    RepairAction repair = new RepairAction(Change.UPDATE, loc, getCode(loc.node), astNode.toString(),
                            change);
                    update(loc, astNode);
                    info.repairs.add(repair);
                    returnCode = C_APPLIED;
                }
                break;
            case Change.REPLACE:
                editHash = DiffUtils.getTypeHash(change.node);
                locHash = DiffUtils.getTypeHash(loc.node);

                if (editHash.equals(locHash) && cStrategy.instCheck(change, loc)) {
                    ASTNode astNode = cStrategy.instantiate(change, loc, info);

                    if (astNode == null) {
                        return C_NOT_INST;
                    }

                    RepairAction repair = new RepairAction(Change.REPLACE, loc, getCode(loc.node), astNode.toString(),
                            change);
                    replace(loc, astNode);
                    info.repairs.add(repair);
                    returnCode = C_APPLIED;
                }
                break;
        }
        if (returnCode == C_APPLIED) {
            addImportDecls(change);
        }
        return returnCode;
    }

    private void addImportDecls(Change change) {
        Set<String> importNames = new HashSet<>(change.requirements.imports);
        importNames.addAll(cStrategy.importNames);
        importNames = getImportNames(importNames, cu.imports());
        ListRewrite lrw = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
        AST ast = cu.getAST();
        for (String importName : importNames) {
            ImportDeclaration decl = ast.newImportDeclaration();
            decl.setName(ast.newName(importName));
            lrw.insertLast(decl, null);
        }
    }

    private Set<String> getImportNames(Set<String> imports, List<ImportDeclaration> importDecls) {
        HashSet<String> importNames = new HashSet<>(imports);
        for (ImportDeclaration importDecl : importDecls) {
            importNames.remove(importDecl.getName().getFullyQualifiedName());
        }
        return importNames;
    }

    private String getCode(Node node) {
        String code;
        try {
            code = doc.get(node.startPos, node.length);
        } catch (BadLocationException e) {
            code = "";
        }
        return code;
    }

    private void replace(TargetLocation loc, ASTNode astNode) {
        rewrite.replace(loc.node.astNode, astNode, null);
    }

    // MOVE Operation is unused now
//    private void move(TargetLocation loc, TargetLocation moveLoc, StructuralPropertyDescriptor descriptor) {
//        ASTNode oldNode = loc.node.astNode;
//        if (descriptor.isChildListProperty() && loc.node.isStatement && loc.node.astNode instanceof Expression) {
//            oldNode = oldNode.getParent();
//        }
//        ASTNode newNode = ASTNode.copySubtree(moveLoc.node.astNode.getAST(), oldNode);
//        delete(loc.node);
//        insert(moveLoc, newNode, descriptor);
//    }

    private void insert(TargetLocation loc, ASTNode astNode, StructuralPropertyDescriptor descriptor) {
        ASTNode parent = loc.node.astNode.getParent();
        if (descriptor.isChildListProperty()) {
            // Handling implicit ExpressionStatement.
            if (loc.node.isStatement && loc.node.astNode instanceof Expression) {
                parent = parent.getParent();
            }
            ListRewrite lrw = rewrite.getListRewrite(parent, (ChildListPropertyDescriptor) descriptor);
            if (loc.kind == TargetLocation.INSERT_BEFORE) {
                Node left = loc.node.getLeft();
                if (loc.node.astNode.getLocationInParent().equals(descriptor)) {
                    lrw.insertBefore(astNode, loc.node.astNode, null);
                } else if (left != null && left.astNode.getLocationInParent().equals(descriptor)) {
                    lrw.insertAfter(astNode, left.astNode, null);
                } else {
                    lrw.insertFirst(astNode, null);
                }
            } else if (loc.kind == TargetLocation.INSERT_AFTER) {
                // Default - insert after.
                Node right = loc.node.getRight();
                if (loc.node.astNode.getLocationInParent().equals(descriptor)) {
                    lrw.insertAfter(astNode, loc.node.astNode, null);
                } else if (right != null && right.astNode.getLocationInParent().equals(descriptor)) {
                    lrw.insertBefore(astNode, right.astNode, null);
                } else {
                    lrw.insertFirst(astNode, null);
                }
            } else if (loc.kind == TargetLocation.INSERT_UNDER) {
                lrw = rewrite.getListRewrite(loc.node.astNode, (ChildListPropertyDescriptor) descriptor);
                lrw.insertFirst(astNode, null);
            }
        } else if (parent != null & parent.getNodeType() == ASTNode.INFIX_EXPRESSION) {
            InfixExpression infix = (InfixExpression) parent;
            ListRewrite lrw = rewrite.getListRewrite(parent, InfixExpression.EXTENDED_OPERANDS_PROPERTY);
            if (loc.kind == TargetLocation.INSERT_BEFORE) {
                if (descriptor.getId().equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId())) {
                    // n, l, r, e
                    lrw.insertFirst(infix.getRightOperand(), null);
                    rewrite.set(infix, InfixExpression.RIGHT_OPERAND_PROPERTY, infix.getLeftOperand(), null);
                    rewrite.set(infix, InfixExpression.LEFT_OPERAND_PROPERTY, astNode, null);
                } else if (descriptor.getId().equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId())) {
                    // l, n, r, e
                    lrw.insertFirst(infix.getRightOperand(), null);
                    rewrite.set(infix, InfixExpression.RIGHT_OPERAND_PROPERTY, astNode, null);
                }
            } else if (loc.kind == TargetLocation.INSERT_AFTER) {
                if (descriptor.getId().equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId())) {
                    // l, n, r, e
                    lrw.insertFirst(infix.getRightOperand(), null);
                    rewrite.set(infix, InfixExpression.RIGHT_OPERAND_PROPERTY, astNode, null);
                } else if (descriptor.getId().equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId())) {
                    // l, r, n, e
                    lrw.insertFirst(astNode, null);
                }
            }
        } else {
            if (descriptor.getId().equals(IfStatement.THEN_STATEMENT_PROPERTY.getId())
                    && loc.kind == TargetLocation.INSERT_AFTER) {
                descriptor = IfStatement.ELSE_STATEMENT_PROPERTY;
            } else if (descriptor.getId().equals(MethodInvocation.NAME_PROPERTY.getId())
                    && loc.kind == TargetLocation.INSERT_BEFORE) {
                descriptor = MethodInvocation.EXPRESSION_PROPERTY;
            }
            rewrite.set(parent, descriptor, astNode, null);
        }
    }

    private void update(TargetLocation loc, ASTNode astNode) {
        rewrite.replace(loc.node.astNode, astNode, null);
    }

    private void delete(Node node) {
        if (node.isStatement && node.astNode instanceof Expression) {
            rewrite.remove(node.astNode.getParent(), null);
        } else {
            rewrite.remove(node.astNode, null);
        }
    }

}
