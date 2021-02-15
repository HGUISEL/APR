package apr.isel.edu.csee.handong;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class JavaASTNodeAnalyzer {
    final static boolean DEBUG = true;

    public static void main (String args[]){
        System.out.print("input your file path: ");
        Scanner sc = new Scanner(System.in);
        String filePath = sc.nextLine();
        File file = new File(filePath);

        String sourceCode;
        try {
            sourceCode = FileUtils.readFileToString(file, "UTF-8");
            JavaASTParser javaASTParser = new JavaASTParser(sourceCode);

            javaASTParser.CountASTNodes();
            javaASTParser.CollectASTParents();

            if (DEBUG) {
                System.out.println("====== Node Type Count ======");
                Map<String, Integer> nodeCountLst = javaASTParser.getNodeCount();
                nodeCountLst.entrySet().forEach((entry) -> System.out.println(
                        "Node Type: " + entry.getKey() + ", Count: " + entry.getValue())
                );
                System.out.println("\n");
                System.out.println("====== Node Parent Analysis ======");
                Map<String, Set<String>> parentLst = javaASTParser.getParentCollection();
                parentLst.entrySet().forEach((entry) -> {
                    System.out.println("Node Type: " + entry.getKey());
                    System.out.println("Parent Nodes:");
                    entry.getValue().forEach(nodes ->
                    System.out.println("    " + nodes));
                });

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
