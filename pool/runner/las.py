import csv
import logging
import numpy as np
import os #nope
import sys
import pandas as pd


def main(argv):
    projects = ["Closure", "Lang", "Math", "Time"]
    root = os.getcwd()
    code_dir = root+"/pool/las/data"

    #simfin 결과 읽기
    LAS_input = pd.read_csv(root+"/pool/outputs/simfin/test_result.csv", 
                            names=['Y_BIC_SHA', 'Y_BIC_Path', 'Y_Project', 'Y_BugId',
                                   'Rank', 'Sim-Score', 'Y^_Project', 
                                   'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk'])
    LAS_input_csv = LAS_input.values



    with open(root+"/pool/outputs/las/LAS_output.csv", 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')

        # writing header
        header = ['DFJ ID', 'Rank', 'orig change info', 'suggested change info']
        csv_writer.writerow(header)

        for i in range(len(LAS_input)):
            if i==0:
                continue
            ## 각 열에서 필요한 정보 읽어오기
            y_bic_sha = LAS_input_csv[i][0]
            y_bic_path = LAS_input_csv[i][1]
            y_project = LAS_input_csv[i][2]
            y_bugId = LAS_input_csv[i][3]
            rank = LAS_input_csv[i][4]
            sim_score = LAS_input_csv[i][5]
            yhat_project = LAS_input_csv[i][6]
            yhat_bic_sha = LAS_input_csv[i][7]
            yhat_bic_path = LAS_input_csv[i][8]
            yhat_bfc_sha = LAS_input_csv[i][9]
            yhat_bfc_path = LAS_input_csv[i][10]
            yhat_bfc_hunk = LAS_input_csv[i][11]

            buggy_sha = ""
            clean_sha = ""

            ## commit-db도 읽기
            ## path is where the D4J framework exists
            commit_db = pd.read_csv("~/paths/defects4j/framework/projects/"+y_project+"/commit-db", 
                                    names=["ID","buggy","clean","num","path"])
            commit_db_csv = commit_db.values


            for j in range(len(commit_db_csv)):
                if int(commit_db_csv[j][0]) == int(LAS_input_csv[i][3]): # if the ID is same
                    buggy_sha = commit_db_csv[j][1] # get the buggy sha from commit-db
                    clean_sha = commit_db_csv[j][2] # get the clean sha from commit-db
                    break
            
            orig_BIC_path = code_dir+"/"+y_project+"_"+y_bugId+"_orig_old.java"
            orig_BFC_path = code_dir+"/"+y_project+"_"+y_bugId+"_orig_new.java"

            os.system("cd "+root+"/pool/commit_collector/data/"+y_project+"/"+y_project+"-"+y_bugId+" ; "
                    + "git checkout -f "+buggy_sha+" ; "
                    + "cp "+y_bic_path+" "+orig_BIC_path+" ; "
                    + "git checkout -f "+clean_sha+" ; "
                    + "cp "+y_bic_path+" "+orig_BFC_path+" ; " )

            ## check out the reccomended commit in the repository and copy the file into code_dir
            rec_BIC_path = code_dir+"/"+y_project+"_"+y_bugId+"_rank-"+rank+"_old.java"
            rec_BFC_path = code_dir+"/"+y_project+"_"+y_bugId+"_rank-"+rank+"_new.java"

            os.system("cd ~/APR_Projects/data/AllBIC/reference/repositories/"+yhat_project+" ; "
                    + "git checkout -f "+yhat_bic_sha+" ; "
                    + "cp "+yhat_bic_path+" "+rec_BIC_path+" ; "
                    + "git checkout -f "+yhat_bfc_sha+" ; "
                    + "cp "+yhat_bfc_path+" "+rec_BFC_path+" ; " )

            # code to run LAS
            LAS_orig_stream = os.popen('cd ~/APR_Projects/LAS/target/ ; '
                                        'java -cp LAS-0.0.1-SNAPSHOT-jar-with-dependencies.jar  main.LAS ' + orig_BIC_path + ' ' + orig_BFC_path)
            LAS_orig_result = str(LAS_orig_stream.read())

            LAS_rec_stream = os.popen('cd ~/APR_Projects/LAS/target/ ; '
                                        'java -cp LAS-0.0.1-SNAPSHOT-jar-with-dependencies.jar  main.LAS ' + rec_BIC_path + ' ' + rec_BFC_path)
            LAS_rec_result = str(LAS_rec_stream.read())

            
            D4J_ID = y_project + y_bugId

            # writing each row values (DFJ ID', 'Rank', 'orig change info', 'suggested change info')
            instance = [D4J_ID, rank, LAS_orig_result, LAS_rec_result] ## data in order of columns
            csv_writer.writerow(instance)

        print('Finished ALL!!')



if __name__ == '__main__':
    main(sys.argv)
