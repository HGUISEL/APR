import csv
import logging
import numpy as np
import os #nope
import sys
import pandas as pd
import getopt



def main(argv):

    try:
        opts, args = getopt.getopt(argv[1:], "h:", ["hash"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)
    hash_input=""
    for o, a in opts:
        if o in ("-h", "--hash"):
            hash_input = a
        else:
            assert False, "unhandled option"

    



    root = os.getcwd()

    target_dir = root+"/target/"+hash_input
    output_dir = target_dir+"/outputs"
    code_dir = output_dir+"/prepare_pool_source"

    os.system("mkdir "+target_dir)
    os.system("mkdir -p "+code_dir)

    os.system("echo \"Finished collecting relevant patches for your bug\" >> "+target_dir+"/status.txt")

    #simfin 결과 읽기
    read_input = pd.read_csv(output_dir+"/simfin/test_result.csv", 
                            names=['Y_BIC_SHA', 'Y_BIC_Path', 'Y_Project', 'Y_BugId',
                                   'Rank', 'Sim-Score', 'Y^_Project', 
                                   'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk'])
    input_csv = read_input.values



    for i in range(len(read_input)):
        if i==0:
            continue
        if input_csv[i][0] == "-":
            continue

        ## 각 열에서 필요한 정보 읽어오기
        y_bic_sha = input_csv[i][0]
        y_project = input_csv[i][2]
        y_bugId = input_csv[i][3]
        rank = input_csv[i][4]
        #sim_score = input_csv[i][5]    # no need
        yhat_project = input_csv[i][6]
        yhat_bic_sha = input_csv[i][7]
        yhat_bic_path = input_csv[i][8]
        yhat_bfc_sha = input_csv[i][9]
        yhat_bfc_path = input_csv[i][10]
        #yhat_bfc_hunk = input_csv[i][11]   # no need


        ## check out the reccomended commit in the repository and copy the file into code_dir
        # if is_D4J == True:
        #     rec_BIC_path = code_dir+"/"+y_project+"_"+y_bugId+"_rank-"+rank+"_old.java"
        #     rec_BFC_path = code_dir+"/"+y_project+"_"+y_bugId+"_rank-"+rank+"_new.java"
        # else:
        rec_BIC_path = code_dir+"/"+y_project+"_rank-"+rank+"_old.java"
        rec_BFC_path = code_dir+"/"+y_project+"_rank-"+rank+"_new.java"

        print("Generating patch #{}...".format(rank))
        print("> Project      : {}".format(yhat_project))
        print("> Int.Commit   : {}".format(yhat_bic_sha))
        print("> Path         : {}".format(yhat_bic_path))

        # os.system("cd /data/codemodel/repositories/"+yhat_project+" ; " + 'su aprweb -c "/usr/bin/git checkout -f '+yhat_bic_sha+'" ; ' + "cp "+yhat_bic_path+" "+rec_BIC_path+" ; ")
        os.system("cd /data/codemodel/repositories/"+yhat_project+" ; "
                + "git checkout -f "+yhat_bic_sha+" ; "
                + "cp "+yhat_bic_path+" "+rec_BIC_path+" ; ")

        print("> Fix.Commit   : {}".format(yhat_bfc_sha))
        print("> Path         : {}".format(yhat_bfc_path))

        # os.system("cd /data/codemodel/repositories/"+yhat_project + " ; " + 'su aprweb -c "/usr/bin/git checkout -f '+yhat_bfc_sha+'" ; ' + "cp "+yhat_bfc_path+" "+rec_BFC_path+" ; ")
        os.system("cd /data/codemodel/repositories/"+yhat_project + " ; "
                + "git checkout -f "+yhat_bfc_sha+" ; "
                + "cp "+yhat_bfc_path+" "+rec_BFC_path+" ; ")

        print()

    os.system("echo \"Finished collecting source code to prepare pool to fix your bug\" >> "+target_dir+"/status.txt")



if __name__ == '__main__':
    main(sys.argv)
