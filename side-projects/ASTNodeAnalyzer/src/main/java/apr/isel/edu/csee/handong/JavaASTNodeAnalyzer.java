package apr.isel.edu.csee.handong;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Collections;

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

    public List<Double> patchPrioritizer(JavaASTParser buggyParser, List<LasOutputParser> LasParsers){

        double commonNodeTypeCount = 0;
        double validChangeCount = 0;
        double patchPurity = 0;

        int commonParentRelationCount = 0;

        List<Double> nodeTypeScore = new ArrayList<Double>() ;
        List<Double> changeCountScore = new ArrayList<Double>() ;
        List<Double> patchPurityScore = new ArrayList<Double>() ;
        List<Double> parentCountScore = new ArrayLsit<Double>() ;

        for (LasOutputParser lasParser : LasParsers){
            commonNodeTypeCount = 0;
            validChangeCount = 0;
            patchPurity = 0;

            // 1. node Type이 얼마나 겹치는지, 그리고 그 Type을 타겟하는 체인지가 LAS의 전체 체인지 중 몇 퍼센트인지
            for (String nodeType : lasParser.getNodeCountMap().keySet()){
                if(buggyParser.getNodeCountMap().get(nodeType) != null){
                    commonNodeTypeCount += 1 ;
                    validChangeCount += lasParser.getNodeCountMap().get(nodeType).intValue() ;
                }
            }
            patchPurity = validChangeCount / lasParser.getChanges().size() ;

            
            commonParentRelationCount = 0 ;

            // 2. parent-child 관계가 얼마나 겹치는지
            for (String child : lasParser.getParentCollectionMap().keySet()){
                List<String> buggyParents = buggyParser.getParentCollectionMap().get(child)
                // child가 있다면
                if(buggyParents != null){
                    //parent 가 얼마나 겹치는지
                    commonParentRelationCount = buggyParents.retainAll(lasParser.getParentCollection().get(child))
                }
            }

            nodeTypeScore.add(new Double(commonNodeTypeCount));
            changeCountScore.add(new Double(validChangeCount));
            patchPurityScore.add(new Double(patchPurity));
            parentCountScore.add(new Double(commonParentRelationCount));

        }

        // min-max normalize each socres except the purity
        int min = 0;
        int max = 0;

        min = Collections.min(nodeTypeScore);
        max = Collections.max(nodeTypeScore);
        for (int i=0; i< nodeTypeScore.size(); i++){
            nodeTypeScore.set(i, new Double((nodeTypeScore.get(i).intValue()-min) /(max-min))) ;
        }

        min = Collections.min(changeCountScore);
        max = Collections.max(changeCountScore);
        for (int i=0; i< changeCountScore.size(); i++){
            changeCountScore.set(i, new Double((changeCountScore.get(i).intValue()-min) /(max-min))) ;
        }

        min = Collections.min(parentCountScore);
        max = Collections.max(parentCountScore);
        for (int i=0; i< parentCountScore.size(); i++){
            parentCountScore.set(i, new Double((parentCountScore.get(i).intValue()-min) /(max-min))) ;
        }

        //calculate final score
        List<Double> finalScore = new ArrayList<Double>
        for (int i=0; i< parentCountScore.size(); i++){
            double typeScore = nodeTypeScore.get(i).doubleValue() * 0.3 + changeCountScore.get(i).doubleValue() * 0.4 + patchPurityScore.get(i).doubleValue() * 0.3 ;
            finalScore.add(new Double(typeScore)) ;

            // not yet decided how to consider parent scores


  
        }

        return finalScore ;


    }
}
