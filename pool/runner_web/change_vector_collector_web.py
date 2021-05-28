import csv
import logging
import numpy as np
import os #nope
import sys
import pandas as pd
import getopt

## system이든 popen 이든 한번의 콜이 끝나면 연속성이 없어진다.
## 특히 문제는 cd로 이동한 게 무효가 되어 매번 필요한 위치는 절대 참조로 넣어야 한다는 점이다.
## 따라서 매번 cd 해줄지, 아니면 크게크게 묶어서 하나의 systme이나 popen을 통해 할 것인지 결정 필요


def main(argv):

    MODEL_SIZE = 15328
    project = ""

    try:
        opts, args = getopt.getopt(argv[1:], "d:", ["defects4J"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)
    is_D4J = False
    for o, a in opts:
        if o in ("-d", "--defects4J"):
            is_D4J = True
        else:
            assert False, "unhandled option"

    projects = ["Closure", "Lang", "Math", "Time"]
    real_projects = ["closure-compiler", "defects4j-lang", "defects4j-math", "joda-time"]

    root = os.getcwd()
    cvc_path = root+"/pool/change_vector_collector/build/distributions/bin/change-vector-collector"

    ## build change-vector-collector if necessary
#    os.system("cd "+root+"/pool/change_vector_collector ; "
#            + "rm -rf ./build/distributions/* ; "
#            + "gradle clean distzip ; "
#            + "cd ./build/distributions ; "
#            + "unzip change-vector-collector.zip ; "
#            + "rm change-vector-collector.zip ; "
#            + "mv ./change-vector-collector/* ./ ; "
#            + "rm -r ./change-vector-collector")

    os.system("rm -rf ./pool/outputs/change_vector_collector_web/* ")


    BFIC_df = pd.read_csv(root+"/pool/outputs/commit_collector_web/BFIC.csv", names=["Project","D4J ID", "Faulty file path","faulty line","FIC sha","BFIC sha"])
    BFIC_csv = BFIC_df.values
    project = BFIC_csv[1][0]
    D4J_ID = BFIC_csv[1][1]



## CVC 작동 안해서 안되서 우회하는 코드 
    real_project = real_projects[projects.index(project)]
    cheat_data_index=0
    cheat_df = pd.read_csv(root+"/pool/outputs/change_vector_collector/Y_"+real_project+".csv", names=[ "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12" ])
    cheat_csv = cheat_df.values

    for i in range(len(cheat_csv)):
            if int(D4J_ID) == int(cheat_csv[i][9].split("-")[1]):
                cheat_data_index = i
                break
    with open(root+"/pool/outputs/change_vector_collector_web/Y.csv", 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')
        csv_writer.writerow([cheat_csv[i][0],cheat_csv[i][1],cheat_csv[i][2],cheat_csv[i][3],cheat_csv[i][4],cheat_csv[i][5],cheat_csv[i][6],cheat_csv[i][7],cheat_csv[i][8],cheat_csv[i][9],cheat_csv[i][10],cheat_csv[i][11]])


    
    with open(root+"/pool/outputs/change_vector_collector/X_"+real_project+".csv", "rt", encoding='UTF-8') as infile:
        read = csv.reader(infile)
        i=0
        for row in read :
            if i == cheat_data_index:
                with open(root+"/pool/outputs/change_vector_collector_web/X.csv", 'w', newline='') as csvfile:
                    csv_writer = csv.writer(csvfile, delimiter=',')
                    csv_writer.writerow(row)
                    break
            i = i+1




## Run change-vector-collector, -g means to use gumtree to generate change vectors
    # os.system(cvc_path+" -g"
    #         +" -i "+root+"/pool/outputs/commit_collector_web/BFIC.csv"
    #         +" -o "+root+"/pool/outputs/change_vector_collector_web/"
    #         +" -p "+ project )
                
    # print("Finished Extracting Change Vectors from "+project)




    ## add dummy data to fit the size of the trained model as a second row
    with open(root+"/pool/outputs/change_vector_collector_web/X.csv", 'a', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')
        zeros = []
        for i in range(0,MODEL_SIZE):
            zeros.append('0')
        csv_writer.writerow(zeros)

    with open(root+"/pool/outputs/change_vector_collector_web/Y.csv", 'a', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')
        dummy = ['dummy','-','-','-','-','-','-','-','-','-','-','-']
        csv_writer.writerow(dummy)





    # copy change vector info ad input of simfin.
    os.system("cat "+root+"/pool/outputs/change_vector_collector_web/X*.csv"
        + " > "+root+"/pool/simfin/testset/X_test.csv ;"
        + "cat "+root+"/pool/outputs/change_vector_collector_web/Y*.csv"
        + " > "+root+"/pool/simfin/testset/Y_test.csv ;")


if __name__ == '__main__':
    main(sys.argv)
