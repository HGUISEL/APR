import logging
import os #nope
import sys



def main(argv):
    # pwd is confix/
    root = os.getcwd()

#   target_bug_list = [name for name in os.listdir("../pool/las/data") ] if os.path.isdir(name)]
    subdir_list = [x[0] for x in os.walk("../pool/las/data")]
    target_bug_list = [i.split('data/')[1] for i in subdir_list if i.find('-') > 0 ]
    target_bug_list.sort()

    for target_bug in target_bug_list:
        #target_bug="Closure-21"
        target_project, target_id = target_bug.split('-')

        os.system("cd results ;" 
                + "defects4j checkout -p "+target_project+" -v "+target_id+"b -w "+target_bug)
        print("Finish defects4j checkout")
        
        os.system("cp "+root+"/coverages/"+target_project.lower()+"/"+target_project.lower()+target_id+"b/coverage-info.obj "
                +  root+"/results/"+target_bug)
        print("Finish copying coverage Info")

        current_bug_dir = root+"/results/"+target_bug
        #cd ${PROJ_NAME_LIST[$i]}-${j}
        
        os.system("cd "+current_bug_dir+" ; "
                +  "defects4j compile")
        print("Finish defects4j compile!!")

        os.system("cd "+current_bug_dir+" ; "
                +   root+"/scripts/config.sh "+target_project+" "+target_id)
        print("Finish config!!")

        
        
        os.system("cd "+current_bug_dir+" ; "
                +   root+"/scripts/confix.sh "+current_bug_dir +" > log.txt")
        print("Finish confix!!")

     
    
        os.system("mkdir "+root+"/results/patches/"+target_bug)
        os.system("cp -r "+current_bug_dir+"/patches/* "+root+"/results/patches/"+target_bug+"/")
        
        # mkdir patch-logs
        
        # if [ -e ${PROJ_NAME_LIST[$i]}-${j}/patch_info ];then
        #     cp ${PROJ_NAME_LIST[$i]}-${j}/patch_info patch-logs/${PROJ_NAME_LIST[$i]}-${j}
        # fi
        
        # rm -rf ${PROJ_NAME_LIST[$i]}-${j}

        



if __name__ == '__main__':
    main(sys.argv)
