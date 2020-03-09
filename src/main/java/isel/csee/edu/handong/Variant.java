package isel.csee.edu.handong;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class Variant{
    private CompilationUnit fullAST;
    private ArrayList<String> statementList;
    private ArrayList<Double> scoreList;
    
    public Variant(){

    }


    //setters
    public void setAST(ComlilationUnit newAST){
        this.fullAST = newAST;
    }
    public void setStamentList(ArrayList<String> statements){
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