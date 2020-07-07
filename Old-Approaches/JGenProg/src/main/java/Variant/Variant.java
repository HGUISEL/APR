package Variant;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

import org.eclipse.jdt.core.dom.*;

public class Variant{
    private CompilationUnit fullAST;
    private ArrayList<String> statementList;
    private ArrayList<Double> scoreList;
    
    public Variant(){

    }


    //setters
    public void setAST(CompilationUnit newAST){
        this.fullAST = newAST;
    }
    public void setStatementList(ArrayList<String> statements){
        this.statementList = statements;
    }
    public void setStatement(int index, String newStatement){
        statementList.set(index, newStatement);
    }
    public void setScoreList(ArrayList<Double> scores){
        this.scoreList = scores;
    }

    //getters
    public CompilationUnit getAST(){
        return fullAST;
    }
    public String getStatement(int index){
        return statementList.get(index);
    }
    public ArrayList<String> getStatementList(){
        return statementList;
    }
    public double getScore(int index){
        return scoreList.get(index);
    }
    public ArrayList<Double> getScoreList(){
        return scoreList;
    }
    




}