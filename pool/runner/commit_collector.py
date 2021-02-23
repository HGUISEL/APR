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
    #root_stream = os.popen('pwd')
    #root = str(pwd_stream.read())
    root = os.getcwd()
    
    for project in projects:
        # 프로젝트 이름의 하위 디렉토리(있다고 가정?)으로 이동
        proj_dir = root+"/pool/commit_collector/data/"+project
        os.system("mkdir "+proj_dir)
 
        # remove column "dummy" when input is updated
        input_df = pd.read_csv(root+"/pool/commit_collector/inputs/"+project+".csv", names=["DefectsfJ ID","Faulty file path","fix faulty line","blame faulty line","dummy"])
        input_csv = input_df.values

        ## commit-db도 읽기
        ## path is where the D4J framework exists
        commit_db = pd.read_csv("~/paths/defects4j/framework/projects/"+project+"/commit-db", names=["ID","buggy","clean","num","link"])
        commit_db_csv = commit_db.values
        


        with open(root+"/pool/outputs/commit_collector/"+project+"_BFIC.csv", 'w', newline='') as csvfile:
            csv_writer = csv.writer(csvfile, delimiter=',')

            # writing header
            header = ['Project','D4J ID','Faulty file path','faulty line','FIC_sha','BFIC_sha']
            csv_writer.writerow(header)

            D4J_ID = ""
            faulty_file_path = ""
            fix_faulty_line = ""
            blame_faulty_line = ""
            FIC_sha = ""
            BFIC_sha = ""

            for i in range(len(input_csv)):
                
                if i==0:
                    continue
                
                D4J_ID = input_csv[i][0]
                faulty_file_path = input_csv[i][1]
                fix_faulty_line = input_csv[i][2]
                blame_faulty_line = input_csv[i][3]

                buggy_sha = ""

                for j in range(len(commit_db_csv)):
                    if int(commit_db_csv[j][0]) == int(input_csv[i][0]): # if the ID is same
                        buggy_sha = commit_db_csv[j][1] # get the buggy sha from commit-db
                        break
                
                os.system("defects4j checkout -p "+project+" -v "+D4J_ID+"b -w "+proj_dir+"/"+project+"-"+D4J_ID)
                #os.system("cd "+project+"-"+D4J_ID)
                os.system("git -C "+proj_dir+"/"+project+"-"+D4J_ID+" checkout "+buggy_sha)
                
                git_stream = os.popen("git -C "+proj_dir+"/"+project+"-"+D4J_ID+" blame -C -C -f -l -L "+str(blame_faulty_line)+","+str(blame_faulty_line)+" "+faulty_file_path)
                foo = str(git_stream.read()).split(' ')
                FIC_sha = foo[0]
                faulty_file_path = foo[1]

                git_stream = os.popen("git -C "+proj_dir+"/"+project+"-"+D4J_ID+" rev-parse "+FIC_sha+"~1")
                #print("git -C "+proj_dir+"/"+project+"-"+D4J_ID+" rev-parse "+FIC_sha+"~1")
                #return
                BFIC_sha = str(git_stream.read()).split('\n')[0]

                # writing each row values ('Project','D4J ID','Faulty file path','faulty line','FIC','BFIC')
                instance = [project, D4J_ID, faulty_file_path, fix_faulty_line, FIC_sha, BFIC_sha] ## data in order of columns
                csv_writer.writerow(instance)

                #os.system("cd ..")

                
        print("Finished collecting FIC and BFIC for "+project)

    #os.system("rm -rf "+root+"/pool/commit_collector/data/* ")



if __name__ == '__main__':
    main(sys.argv)
