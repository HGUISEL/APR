package apr.isel.edu.csee.handong;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class JavaASTParser {
    private String sourceCode;
    private CompilationUnit compilationUnit;
    public HashMap<String, Integer> nodeCount = new HashMap<String, Integer>();
    public HashMap<String, Set<String>> parentCollection = new HashMap<String, Set<String>>();

    JavaASTParser(String sourceCode) {
        this.sourceCode = sourceCode;
        this.compilationUnit = this.setASTParser(sourceCode);
    }

    public CompilationUnit setASTParser(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.JLS_Latest);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        char[] content = sourceCode.toCharArray();

        parser.setSource(content);
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
                JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        String[] sources = {};
        String[] classPaths = {};
        parser.setEnvironment(classPaths, sources, null, true);
        parser.setResolveBindings(true);
        parser.setCompilerOptions(options);
        parser.setIgnoreMethodBodies(false);
        parser.setStatementsRecovery(true);

        final CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        return compilationUnit;
    }

    public void CollectASTParents(){
        this.compilationUnit.accept(new ASTVisitor(){
            @Override
            public boolean preVisit2(ASTNode node) {
                ASTNode parentNode = node.getParent();
                if (parentNode == null ) return super.preVisit2(node);

                String currentNodeName = node.getClass().getSimpleName();
                String parentNodeName = parentNode.getClass().getSimpleName();
                parentCollection.putIfAbsent(currentNodeName, new HashSet<String>());
                Set<String> parentSet = parentCollection.get(currentNodeName);
                parentSet.add(parentNodeName);

                parentCollection.put(currentNodeName, parentSet);
                return super.preVisit2(node);
            }
        });
    }

    public void CountASTNodes(){
        this.compilationUnit.accept(new ASTVisitor(){
            @Override
            public boolean preVisit2(ASTNode node) {
                String nodeName = node.getClass().getSimpleName();
                nodeCount.putIfAbsent(nodeName, 0);
                nodeCount.put(nodeName, nodeCount.get(nodeName) + 1);
                return super.preVisit2(node);
            }
        });
    }


    public String getSourceCode() {
        return sourceCode;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public HashMap<String, Integer> getNodeCount() {
        return nodeCount;
    }

    public HashMap<String, Set<String>> getParentCollection() {
        return parentCollection;
    }


}