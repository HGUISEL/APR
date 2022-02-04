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

    try:
        opts, args = getopt.getopt(argv[1:], "d:i:h:", ["defects4J", "input", "hash"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)
    is_D4J = False
    is_direct_input = False
    hash_input = ""
    for o, a in opts:
        if o in ("-d", "--defects4J"):
            is_D4J = True
        elif o in ("-i", "--input"):
            input_string = a
            is_direct_input = True
        elif o in ("-h", "--hash"):
            hash_input = a
        else:
            assert False, "unhandled option"

    root = os.getcwd()

    target_dir = root+"/target/"+hash_input
    output_dir = target_dir+"/outputs"
    os.system("mkdir "+target_dir)
    os.system("mkdir -p "+output_dir+"/commit_collector")


    D4J_ID = "-"
    fix_faulty_line = "---"
    blame_faulty_line = "---"


    # remove column "dummy" when input is updated
    # input은 어차피 한 줄이다. 편의상 csv로 했을 뿐.
    # d4J가 아닐 경우 여기에 buggy sha가 추가로 필요하다.
    # d4j일때는 project에 Chart-3 이렇게 기록해야 한다.
    if is_direct_input == False:
        input_df = pd.read_csv(root+"/pool/commit_collector/inputs/input.csv", names=["Project","Faulty file path","faulty line","buggy sha","url","dummy"])
        input_csv = input_df.values

        project = input_csv[1][0]
        faulty_file_path = input_csv[1][1]
        faulty_line = input_csv[1][2]
        buggy_sha = input_csv[1][3]
        project_url = input_csv[1][4]

    ## 그냥 string으로 input이 주어진다면?
    else:
        input_list = input_string.split(',')
        project = input_list[0]
        
        if is_D4J == False:
            faulty_file_path = input_list[1]
            fix_faulty_line = input_list[2]
            blame_faulty_line = fix_faulty_line
            buggy_sha = input_list[3]
            project_url = input_list[4]





    ##임시로... d4j를 others처럼 하기 위한 작업
    # D4J_project, D4J_ID = project.split("-")
    # project = D4J_project


    if is_D4J:
        project, D4J_ID = project.split("-")

        ## 미리 준비한 데이터에서 읽어오기
        d4j_input_df = pd.read_csv(root+"/pool/commit_collector/inputs/"+project+".csv", names=["DefectsfJ ID","Faulty file path","fix faulty line","blame faulty line","dummy"])
        d4j_input_csv = d4j_input_df.values
        for i in range(len(d4j_input_csv)):
            if i==0:
                continue

            if int(d4j_input_csv[i][0]) == int(D4J_ID):
                faulty_file_path = d4j_input_csv[i][1]
                fix_faulty_line = d4j_input_csv[i][2]
                blame_faulty_line = d4j_input_csv[i][3]
                break

        ## commit-db 읽기
        ## path is where the D4J framework exists
        commit_db = pd.read_csv("/home/codemodel/hans/paths/defects4j/framework/projects/"+project+"/commit-db", names=["ID","buggy","clean","num","link"])
        commit_db_csv = commit_db.values
        for i in range(len(commit_db_csv)):
            if int(commit_db_csv[i][0]) == int(D4J_ID): # if the ID is same
                buggy_sha = commit_db_csv[i][1] # get the buggy sha from commit-db
                break

        ## prepare target project repository
        os.system("cd "+ target_dir+" ;"
                + "defects4j checkout -p "+project+" -v "+D4J_ID+"b -w "+project)
    
    ##### 아닌 경우 ####
    ## 필요한 정보는 input.csv에서 이미 읽어왓기에 clone만 해주면 된다.
    else:
         os.system("cd " + target_dir + " ; "
                 + "git clone "+ project_url + " "+ project) 


    ## git operations: checkout - blame - revparse

    # os.system("git -C "+target_dir+"/"+project+" checkout "+buggy_sha)
    print("git -C "+target_dir+"/"+project+" blame -C -C -f -l -L "+str(blame_faulty_line)+","+str(blame_faulty_line)+" "+faulty_file_path)
    git_stream = os.popen("git -C "+target_dir+"/"+project+" blame -C -C -f -l -L "+str(blame_faulty_line)+","+str(blame_faulty_line)+" "+faulty_file_path)
    foo = str(git_stream.read()).split(' ')
    FIC_sha = foo[0]

    # 이렇게 하면 오히려 실제 고치는 장소랑 바뀌어버린다... 패스가 바뀌는게 문제
    # faulty_file_path = foo[1]

    git_stream = os.popen("git -C "+target_dir+"/"+project+" rev-parse "+FIC_sha+"~1")
    BFIC_sha = str(git_stream.read()).split('\n')[0]


    with open(output_dir+"/commit_collector/BFIC.csv", 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')

        # writing header
        header = ['Project','D4J ID','Faulty file path','faulty line','FIC_sha','BFIC_sha']
        csv_writer.writerow(header)

        # writing each row values ('Project','D4J ID','Faulty file path','faulty line','FIC','BFIC')
        instance = [project, D4J_ID, faulty_file_path, fix_faulty_line, FIC_sha, BFIC_sha] ## data in order of columns
        csv_writer.writerow(instance)

                
        print("Finished collecting FIC and BFIC for "+project)

    #os.system("rm -rf "+root+"/pool/commit_collector/data/* ")

    os.system("echo \"Finished collecting FIC for your bug\" > "+target_dir+"/status.txt")



if __name__ == '__main__':
    main(sys.argv)
