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
    os.system("cd "+root+"/pool/change_vector_collector ; "
            + "rm -rf ./build/distributions/* ; "
            + "gradle clean distzip ; "
            + "cd ./build/distributions ; "
            + "unzip change-vector-collector.zip ; "
            + "rm change-vector-collector.zip ; "
            + "mv ./change-vector-collector/* ./ ; "
            + "rm -r ./change-vector-collector")


    input_df = pd.read_csv(root+"/pool/commit_collector/inputs/input.csv", names=["Project","Faulty file path","faulty line","buggy sha","url","dummy"])
    input_csv = input_df.values
    project = input_csv[1][0]

        
    if(is_D4J):
        project, foo = project.split("-")
        real_project = real_projects[projects.index(project)]
    else:
        real_project = project

    ## Run change-vector-collector, -g means to use gumtree to generate change vectors
    os.system(cvc_path+" -g"
            +" -i "+root+"/pool/outputs/commit_collector_web/BFIC.csv"
            +" -o "+root+"/pool/outputs/change_vector_collector_web/"
            +" -p "+ project )
                
    print("Finished Extracting Change Vectors from "+project)




    ## add dummy data to fit the size of the trained model
    with open(root+"/pool/outputs/change_vector_collector_web/X_"+project+".csv", 'a', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')
        zeros = []
        for i in range(0,MODEL_SIZE):
            zeros.append('0')
        csv_writer.writerow(zeros)

    with open(root+"/pool/outputs/change_vector_collector_web/Y_"+project+".csv", 'a', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')
        dummy = ['dummy','-','-','-','-','-','-','-','-','-','-','-']
        csv_writer.writerow(dummy)





    # copy change vector info ad input of simfin.
    os.system("cp "+root+"/pool/outputs/change_vector_collector_web/X_*"
        + " "+root+"/pool/simfin/testset/X_test.csv ;"
        + "cp "+root+"/pool/outputs/change_vector_collector_web/Y_*"
        + " "+root+"/pool/simfin/testset/Y_test.csv ;")

if __name__ == '__main__':
    main(sys.argv)