package com.github.thwak.confix.pool.changes;

import com.github.thwak.confix.diff.Node;
import com.github.thwak.confix.pool.contexts.Context;
import com.github.thwak.confix.pool.contexts.ContextIdentifier;
import com.github.thwak.confix.pool.models.Script;
import com.github.thwak.confix.pool.utils.Converter;
import com.github.thwak.confix.util.IOUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import script.ScriptGenerator;
import script.model.EditOp;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangePoolGenerator {
    public ChangePool pool;
    public List<Integer> changeList = new ArrayList<Integer>();
    public List<String> postfixList = new ArrayList<String>(Arrays.asList("++", "--"));
    public List<String> infixList = new ArrayList<String>(Arrays.asList("==", "!=", "<", "<=", ">", ">=", "&&", "||", "+", "-", "*", "%", "/", "+=", "-="));
    public List<String> prefixList = new ArrayList<String>(Arrays.asList("!", "++", "--"));
    public List<String> fixList = null;
    private Context context;

    public ChangePoolGenerator() {
        pool = new ChangePool();
    }

    public void collect(Script script) {
        Integer newChangeHash;
        Change revChange = null;

        for (Change c : script.changes.keySet()) {
            ContextIdentifier identifier = pool.getIdentifier();
            List<EditOp> ops = script.changes.get(c);
            for (EditOp op : ops) {
                context = identifier.getContext(op);
                updateMethod(c);

                String nodeType = c.node.label.split("::")[0]; // for Insert and Delete
                String locationType = c.location.label.split("::")[0]; // for Replace and Update

                if (c.type.equals(Change.INSERT) || c.type.equals(Change.DELETE)) {

                    if (nodeType.equals("InfixExpression")) {
                        fixList = infixList;
                    } else if (nodeType.equals("PostfixExpression")) {
                        fixList = postfixList;
                    } else if (nodeType.equals("PrefixExpression")) {
                        fixList = prefixList;
                    }

                    String reverseChangeType = c.type.equals(Change.INSERT) ? Change.DELETE : Change.INSERT;
                    revChange = cloneChange(c, reverseChangeType, c.location.value, locationType, true);
                    addNewChange(revChange);

                } else {
                    if (locationType.equals("InfixExpression")) {
                        fixList = infixList;
                    } else if (locationType.equals("PostExpression")) {
                        fixList = postfixList;
                    } else if (locationType.equals("PrefixExpression")) {
                        fixList = prefixList;
                    }
                }

                if (fixList != null) {
                    switch (c.type) {
                        case Change.INSERT:
                        case Change.DELETE:
                            for (String newInfix : fixList) {
                                Change change = cloneChange(c, c.type, newInfix, locationType, true);
                                addNewChange(change);
                            }
                            break;

                        case Change.UPDATE:
                        case Change.REPLACE:
                            for (String newInfix : fixList) {
                                Change change = cloneChange(c, c.type, newInfix, locationType, false);
                                addNewChange(change);

                                if (c.type.equals(Change.REPLACE)) {
                                    change = cloneChange(c, Change.UPDATE, newInfix, locationType, false);
                                    addNewChange(change);
                                }
                            }
                            break;
                    }
                }

                newChangeHash = createHash(c);
                if (changeList.contains(newChangeHash)) {
                    continue;
                }
                changeList.add(newChangeHash);
                pool.add(c);
                printGeneratedChange(c);
            }
        }
    }

    private void addNewChange(Change change) {
        if (change != null) {
            pool.add(change);
            printGeneratedChange(change);
        }
    }

    private Integer createHash(Change change) {
        return (change.type + change.node.label + change.location.label).hashCode();
    }

    private Change cloneChange(Change change, String operator, String infix, String locationType, boolean isInsertOrDelete) {
        Integer newChangeHash = 0;
        Change cloneChange = new Change(change.id, operator, change.node, change.location);
        if (isInsertOrDelete) {
            cloneChange.node.label = locationType + "::" + infix;
            cloneChange.node.value = infix;
        } else {
            cloneChange.location.label = locationType + "::" + infix;
            cloneChange.location.value = infix;
        }

        newChangeHash = createHash(cloneChange);
        if (changeList.contains(newChangeHash)) {
            return null;
        }

        changeList.add(newChangeHash);
        return cloneChange;
    }

    private void printGeneratedChange(Change c) {
        System.out.println("Added Change type: " + c.type);
        System.out.println("Added Change node: " + c.node.label);
        System.out.println("Added Change location: " + c.location.label);
    }

    private void updateMethod(Change c) {
        Node n = c.node;
        while (n.parent != null && n.parent.type != ASTNode.METHOD_DECLARATION) {
            n = n.parent;
        }
        StringBuffer sb = new StringBuffer(c.id);
        if (n.parent == null) {
            sb.append(":");
        } else {
            sb.append(":");
            if (n.parent.astNode != null) {
                MethodDeclaration md = (MethodDeclaration) n.parent.astNode;
                sb.append(md.getName().toString());
            }
            sb.append(":");
            sb.append(n.parent.startPos);
        }
        c.id = sb.toString();
    }

    public void collect(List<File> bugFiles, List<File> cleanFiles) {
        try {
            for (int i = 0; i < bugFiles.size(); i++) {

                if (bugFiles.get(i) == null || cleanFiles.get(i) == null) {
                    continue;
                }

                System.out.println("buggy file : "+bugFiles.get(i).getName());
				System.out.println("clean file : "+cleanFiles.get(i).getName());

                // Generate EditScript from before and after.
                String oldCode = IOUtils.readFile(bugFiles.get(i));
                String newCode = IOUtils.readFile(cleanFiles.get(i));

                Tree before = TreeBuilder.buildTreeFromFile(bugFiles.get(i));
                Tree after = TreeBuilder.buildTreeFromFile(cleanFiles.get(i));

                if (before == null || after == null) {
                    System.out.println("Tree is null");
                }

                EditScript editScript = ScriptGenerator.generateScript(before, after);
                // Convert EditScript to Script.
                editScript = Converter.filter(editScript);
                EditScript combined = Converter.combineEditOps(editScript);
                Script script = Converter.convert("0", combined, oldCode, newCode);
                collect(script);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}