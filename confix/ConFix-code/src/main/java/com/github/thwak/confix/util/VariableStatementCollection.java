// package com.github.thwak.confix.util;

// import org.eclipse.jdt.core.JavaCore;
// import org.eclipse.jdt.core.dom.*;

// import java.lang.reflect.Method;
// import java.util.*;

// public class VariableStatementCollection {
//     private String sourceCode;
//     private CompilationUnit compilationUnit;
//     public HashMap<String, ArrayList<String>> variableDelclarationStatementMap = new HashMap<>();

//     void JavaASTParser(String sourceCode) {
//         this.sourceCode = sourceCode;
//         this.compilationUnit = this.setASTParser(sourceCode);
//     }

//     public CompilationUnit setASTParser(String sourceCode) {
//         ASTParser parser = ASTParser.newParser(AST.JLS8);

//         parser.setKind(ASTParser.K_COMPILATION_UNIT);
//         char[] content = sourceCode.toCharArray();

//         parser.setSource(content);
//         Map<String, String> options = JavaCore.getOptions();
//         options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
//         options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
//                 JavaCore.VERSION_1_8);
//         options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
//         String[] sources = {};
//         String[] classPaths = {};
//         parser.setEnvironment(classPaths, sources, null, true);
//         parser.setResolveBindings(true);
//         parser.setCompilerOptions(options);
//         parser.setIgnoreMethodBodies(false);
//         parser.setStatementsRecovery(true);

//         final CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

//         return compilationUnit;
//     }

//     public void getAllVariableDeclarationStatements(){
//         this.compilationUnit.accept(new ASTVisitor(){
//             @Override
//             public boolean visit(VariableDeclarationStatement node) {
//                 ArrayList<String> lst= new ArrayList<>();
// //                System.out.println(node.getType().toString());
//                 node.accept(new ASTVisitor() {
//                     @Override
//                     public boolean visit(SimpleName node) {
//                         lst.add(node.toString());
//                         return super.visit(node);
//                     }
//                 });
// //
// //                System.out.println(lst.get(1));
// //                System.out.println(node.toString());
//                 variableDelclarationStatementSet.put(lst.get(1), node.getType().toString());
//                 return super.visit(node);
//             }
//         });
//     }

//     public void visitAll(){
//         this.compilationUnit.accept(new ASTVisitor() {
//             @Override
//             public boolean preVisit2(ASTNode node) {
//                 if (node.getNodeType() == ASTNode.METHOD_INVOCATION){
//                     ASTNode exp = ((MethodInvocation)node).getExpression();
//                     String type = variableDelclarationStatementSet.get(exp.toString());

//                     if (type == null ) return super.preVisit2(node);
//                     if (type.equals("String")){
//                         String temp = "";
//                         Class c  = temp.getClass();

//                         HashSet<String> set =  new HashSet<>();

//                         for (Method method : c.getDeclaredMethods()){
//                             set.add(method.getName());
//                         }

//                         System.out.println("==================");
//                         System.out.println("Identifier : " + exp.toString());
//                         for (String a : set){
//                             System.out.println(a);
//                         }
//                         System.out.println("==================");
//                     }
//                 }
//                 return super.preVisit2(node);
//             }
//         });
//     }

//     public void CollectASTParents(){
//         this.compilationUnit.accept(new ASTVisitor(){
//             @Override
//             public boolean preVisit2(ASTNode node) {
//                 ASTNode parentNode = node.getParent();
//                 if (parentNode == null ) return super.preVisit2(node);
// //                
// //                String currentNodeName = node.getClass().getSimpleName();
// //                String parentNodeName = parentNode.getClass().getSimpleName();
//                 parentCollectionMap.putIfAbsent(node, new HashSet<ASTNode>());
//                 Set<ASTNode> parentSet = parentCollectionMap.get(node);
//                 parentSet.add(parentNode);
                
//                 if (node.getNodeType() == 42) {
//                 	SimpleName s = (SimpleName) node;
//                 	System.out.println(s.getFullyQualifiedName());
//                 	ITypeBinding v =  (ITypeBinding) s.resolveTypeBinding();
//                 	if(v!=null)
//                 		System.out.println(v.getName());
// //                	System.out.println("de");
//                 }

                 
                
//                 parentCollectionMap.put(node, parentSet);
//                 return super.preVisit2(node);
//             }
//         });
//     }

//     public void CountASTNodes(){
//         this.compilationUnit.accept(new ASTVisitor(){
//             @Override
//             public boolean preVisit2(ASTNode node) {
//                 String nodeName = node.getClass().getSimpleName();
//                 nodeCountMap.putIfAbsent(nodeName, 0);
//                 nodeCountMap.put(nodeName, nodeCountMap.get(nodeName) + 1);
//                 return super.preVisit2(node);
//             }
//         });
//     }

//     public HashMap<String, String> getVDS(){
//         return variableDelclarationStatementSet;
//     }

//     public String getSourceCode() {
//         return sourceCode;
//     }

//     public CompilationUnit getCompilationUnit() {
//         return compilationUnit;
//     }

//     public HashMap<String, Integer> getNodeCountMap() {
//         return nodeCountMap;
//     }

//     public HashMap<ASTNode, Set<ASTNode>> getParentCollectionMap() {
//         return parentCollectionMap;
//     }


// }