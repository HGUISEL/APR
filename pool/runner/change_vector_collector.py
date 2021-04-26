import csv
import logging
import numpy as np
import os #nope
import sys
import pandas as pd

## system이든 popen 이든 한번의 콜이 끝나면 연속성이 없어진다.
## 특히 문제는 cd로 이동한 게 무효가 되어 매번 필요한 위치는 절대 참조로 넣어야 한다는 점이다.
## 따라서 매번 cd 해줄지, 아니면 크게크게 묶어서 하나의 systme이나 popen을 통해 할 것인지 결정 필요


def main(argv):

    projects = ["Closure", "Lang", "Math", "Time"]
    project_urls = ["https://github.com/google/closure-compiler", "https://github.com/haidaros/defects4j-lang", "https://github.com/haidaros/defects4j-math", "https://github.com/JodaOrg/joda-time"]
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
    
    
    for i in range(len(projects)):
        ## Run change-vector-collector, -g means to use gumtree to generate change vectors
        os.system(cvc_path+" -g"
                +" -u "+ real_projects[i]  ## url option is modified to take in project names
                +" -i "+root+"/pool/outputs/commit_collector/"+projects[i]+"_BFIC.csv"
                +" -o "+root+"/pool/outputs/change_vector_collector/")
                
        print("Finished Extracting Change Vectors from "+projects[i])


    ## combine change vector info of each project into single csv file.
    os.system("cat "+root+"/pool/outputs/change_vector_collector/X_*"
            +    " > "+root+"/pool/simfin/testset/X_test.csv ;"
            + "cat "+root+"/pool/outputs/change_vector_collector/Y_*"
            +    " > "+root+"/pool/simfin/testset/Y_test.csv ;")

if __name__ == '__main__':
    main(sys.argv)
