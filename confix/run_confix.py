import logging
import os  # nope
import sys
import pandas as pd


def main(argv):
    # pwd is confix/
    root = os.getcwd()
    # currently, we are running confix in APR_Contents/APR/confix directory

#   target_bug_list = [name for name in os.listdir("../pool/las/data") ] if os.path.isdir(name)]
    subdir_list = [x[0] for x in os.walk("../pool/las/data")]
    target_bug_list = [i.split('data/')[1]
                       for i in subdir_list if i.find('-') > 0]
    target_bug_list.sort()

    for target_bug in target_bug_list:
        # target_bug="Closure-14"
        target_project, target_id = target_bug.split('-')


        perfect_info = pd.read_csv("../pool/commit_collector/inputs/"+target_project+".csv",
                                   names=['D4J_ID', 'faulty_path', 'fix_faulty_line', 'blame_faulty_line', 'dummy'])
        perfect_info_csv = perfect_info.values
        perfect_faulty_path = ""
        perfect_faulty_line = ""

        for i in range(len(perfect_info_csv)):
            if i == 0:
                continue
            if perfect_info_csv[i][0] == target_id:
                perfect_faulty_path = perfect_info_csv[i][1]
                perfect_faulty_line = perfect_info_csv[i][2]
                break
        perfect_faulty_class, foo = perfect_faulty_path.split(".")
        perfect_faulty_class = perfect_faulty_class.replace("/", ".")

        os.system('rm -rf results/'+target_bug)

        os.system("cd results ;"
                  + "defects4j checkout -p "+target_project+" -v "+target_id+"b -w "+target_bug)
        print("Finish defects4j checkout")

        os.system("cp "+root+"/coverages/"+target_project.lower()+"/"+target_project.lower()+target_id+"b/coverage-info.obj "
                  + root+"/results/"+target_bug)
        print("Finish copying coverage Info")

        current_bug_dir = root+"/results/"+target_bug
        # cd ${PROJ_NAME_LIST[$i]}-${j}

        os.system("cd "+current_bug_dir+" ; "
                  + "defects4j compile")
        print("Finish defects4j compile!!")

        os.system("cd "+current_bug_dir+" ; "
                  + root+"/scripts/config.sh "+target_project+" "+target_id + " " + perfect_faulty_class + " " + perfect_faulty_line)
        print("Finish config!!")

        os.system("cd "+current_bug_dir+" ; "
                  + root+"/scripts/confix.sh . >>log.txt 2>&1")

        print("Finish confix!!")

        os.system("mkdir "+root+"/results/patches/"+target_bug)
        os.system("cp -r "+current_bug_dir+"/patches/* " +
                  root+"/results/patches/"+target_bug+"/")
        
        # mkdir patch-logs

        # if [ -e ${PROJ_NAME_LIST[$i]}-${j}/patch_info ];then
        #     cp ${PROJ_NAME_LIST[$i]}-${j}/patch_info patch-logs/${PROJ_NAME_LIST[$i]}-${j}
        # fi

        # rm -rf ${PROJ_NAME_LIST[$i]}-${j}

        # break


if __name__ == '__main__':
    main(sys.argv)
