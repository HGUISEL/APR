import logging
import os  # nope
import sys
import pandas as pd
import getopt


def main(argv):
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


    # pwd is APR_Contents/APR/
    root = os.getcwd()
    # currently, we are running confix in APR directory


    perfect_info = pd.read_csv(root+"/pool/outputs/commit_collector_web/BFIC.csv",
                                names=['Project','D4J ID','Faulty file path','faulty line','FIC sha','BFIC sha'])
    perfect_info_csv = perfect_info.values

    target_project = perfect_info_csv[1][0]
    target_id = perfect_info_csv[1][1]
    perfect_faulty_path = perfect_info_csv[1][2]
    perfect_faulty_line = perfect_info_csv[1][3]

    perfect_faulty_class, foo = perfect_faulty_path.split(".")
    perfect_faulty_class = perfect_faulty_class.replace("/", ".")

    target_dir = root+"/target/"+target_project


    ## build confix and move it to the library
    os.system("cd ./confix/ConFix-code ;"
            + "mvn clean package ;"
            + "cp target/confix-0.0.1-SNAPSHOT-jar-with-dependencies.jar /home/DPMiner/APR_Contents/APR/confix/lib/confix-ami_torun.jar")



    ## prepare setup before running confix

    ### for D4J projects
    if is_D4J == True:
        os.system("rm -rf "+root+"/target/* ;"
                    + "defects4j checkout -p "+target_project+" -v "+target_id+"b -w "+root+"/target/"+target_project)

        os.system("cp "+root+"/confix/coverages/"+target_project.lower()+"/"+target_project.lower()+target_id+"b/coverage-info.obj "
                    + root+"/target/"+target_project)
        # print("Finish copying coverage Info")

        os.system("cd "+target_dir+" ; "
                    + "defects4j compile")
        # print("Finish defects4j compile!!")

        os.system("cd "+target_dir+" ; "
                    + root+"/confix/scripts/config.sh "+target_project+" "+target_id + " " + perfect_faulty_class + " " + perfect_faulty_line)
        print("Finish config!!")

    ### for non-D4J projects
    # else:
        # build
        # fill up properties file


    # os.system("cd "+target_dir+" ; "
    #             + root+"/confix/scripts/confix.sh . >>log.txt 2>&1")
    os.system("cd "+target_dir+" ; "
            + "/usr/lib/jvm/java-8-openjdk-amd64/bin/java "
            + "-Xmx4g -cp ../../confix/lib/las.jar:../../confix/lib/confix-ami_torun.jar "
            + "-Duser.language=en -Duser.timezone=America/Los_Angeles com.github.thwak.confix.main.ConFix "
            + "> log.txt")
    print("Finish confix!!")

    # os.system("mkdir "+root+"/target/"+target_project+"/patches")

    os.system("cd /home/DPMiner/ ; "
            + "git diff /home/DPMiner/APR_Contents/APR/target/"+target_project+"/patches/0/"+perfect_faulty_path
                    + " /home/DPMiner/APR_Contents/APR/target/"+target_project + perfect_faulty_path
                    + " > /home/DPMiner/APR_Contents/APR/target/diff_file.txt")

    # # 패치의 path
    # /home/DPMiner/APR_Contents/APR/target/Math/patches/0/org/apache/commons/math/stat/Frequency.java

    # # 주어지는 path
    # src/main/java/org/apache/commons/math/stat/Frequency.java




if __name__ == '__main__':
    main(sys.argv)