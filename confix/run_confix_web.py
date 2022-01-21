import logging
import os  # nope
import sys
import pandas as pd
import getopt


def main(argv):
    try:
        opts, args = getopt.getopt(argv[1:], "d:i:h:", ["defects4J", "input", "hash"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)
    is_D4J = False
    hash_input = ""
    for o, a in opts:
        if o in ("-d", "--defects4J"):
            is_D4J = True
        elif o in ("-i", "--input"):
            input_string = a
        elif o in ("-h", "--hash"):
            hash_input = a
        else:
            assert False, "unhandled option"

    


    root = os.getcwd()
    
    target_dir = root+"/target/"+hash_input
    output_dir = target_dir+"/outputs"

    just_target = target_dir


    # pwd is APR_Contents/APR/
    root = os.getcwd()
    # currently, we are running confix in APR directory


    perfect_info = pd.read_csv(output_dir+"/commit_collector/BFIC.csv",
                                names=['Project','D4J ID','Faulty file path','faulty line','FIC sha','BFIC sha'])
    perfect_info_csv = perfect_info.values

    target_project = perfect_info_csv[1][0]
    target_id = perfect_info_csv[1][1]
    perfect_faulty_path = perfect_info_csv[1][2]
    perfect_faulty_line = perfect_info_csv[1][3]

    perfect_faulty_class, foo = perfect_faulty_path.split(".")
    perfect_faulty_class = perfect_faulty_class.replace("/", ".")

    target_dir = target_dir +"/"+ target_project




    ## build confix and move it to the library
    os.system("cd ./confix/ConFix-code ;"
            + "mvn clean package ;"
            + "cp target/confix-0.0.1-SNAPSHOT-jar-with-dependencies.jar /home/codemodel/hans/APR/confix/lib/confix-ami_torun.jar")







    ## prepare setup before running confix

    ### for D4J projects
    if is_D4J == True:
        os.system("rm -rf "+target_dir+ " ;"
                    + "defects4j checkout -p "+target_project+" -v "+target_id+"b -w "+target_dir)

        os.system("cp "+root+"/confix/coverages/"+target_project.lower()+"/"+target_project.lower()+target_id+"b/coverage-info.obj "
                    + target_dir)

        os.system("cd "+target_dir+" ; "
                    + root+"/confix/scripts/config.sh "+target_project+" "+target_id + " " + perfect_faulty_class + " " + perfect_faulty_line)
        
        os.system("cd "+target_dir+" ; "
                + "echo \"pool.source=" +just_target+ "/outputs/prepare_pool_source\" >> confix.properties ; ")
        

    ### for non-D4J projects
    else:
        ### parse the inputs into items
        input_list = input_string.split(',')
        sourcePath = input_list[0]
        targetPath = input_list[1]
        testList = input_list[2]
        compileClassPath = input_list[4]
        testClassPath = input_list[3]
        buildTool = input_list[5]

        ### copy dummy coverage info file
        os.system("cp "+root+"/confix/coverages/math/math1b/coverage-info.obj "
                    + target_dir)


        os.system("cd "+target_dir+" ; "
                + "cp "+root+"/confix/properties/confix.properties ./")
        
        ### fill up the confix.property
        os.system("cd "+target_dir+" ; "
                + "echo \"src.dir=" +sourcePath+ "\" >> confix.properties ; "
                + "echo \"target.dir=" +targetPath+ "\" >> confix.properties ; "
                + "echo \"cp.compile=" +compileClassPath+ "\" >> confix.properties ; "
                + "echo \"cp.test=" +testClassPath+ "\" >> confix.properties ; "
                + "echo \"projectName=" +target_project+ "\" >> confix.properties ; "
                + "echo \"pFaultyClass=" +perfect_faulty_class+ "\" >> confix.properties ; "
                + "echo \"pFaultyLine=" +perfect_faulty_line+ "\" >> confix.properties ; "
                + "echo \"pool.source=" +just_target+ "/outputs/prepare_pool_source\" >> confix.properties ; "
                + "echo \"" +testList+ "\" > tests.all ; "
                + "echo \"" +testList+ "\" > tests.relevant ; "
                + "echo \"" +testList+ "\" > tests.trigger ; ")

        print("buildTool is:"+buildTool)

        if buildTool == "gradle" or buildTool == "Gradle":
            os.system("cd "+target_dir+" ; "
                    + "/home/codemodel/hans/paths/gradle-6.8.3/bin/gradle build")
            # print("are you in?")

        elif buildTool == "maven" or buildTool == "Maven" or buildTool == "mvn":
            os.system("cd "+target_dir+" ; "
                    + "/home/codemodel/paths/apache-maven-3.8.3/bin/mvn compile")

    print("Configuration finished.")

    print("Executing ConFix...")

    # os.system("cd "+target_dir+" ; "
    #             + root+"/confix/scripts/confix.sh . >>log.txt 2>&1")
    print("cd "+target_dir+" ; "
            + "/usr/lib/jvm/java-8-openjdk-amd64/bin/java "
            # + "java "
            + "-Xmx4g -cp ../../../confix/lib/las.jar:../../../confix/lib/confix-ami_torun.jar "
            + "-Duser.language=en -Duser.timezone=America/Los_Angeles com.github.thwak.confix.main.ConFix "
            + "> log.txt")
    os.system("cd "+target_dir+" ; "
            + "/usr/lib/jvm/java-8-openjdk-amd64/bin/java "
            # + "java "
            + "-Xmx4g -cp ../../../confix/lib/las.jar:../../../confix/lib/confix-ami_torun.jar "
            + "-Duser.language=en -Duser.timezone=America/Los_Angeles com.github.thwak.confix.main.ConFix "
            + "> log.txt")
    print("ConFix Execution Finished.")


    os.system("echo \"end\" >> "+ just_target+"/status.txt")


    if not os.path.isfile(target_dir + "/patches/0/" + perfect_faulty_path):
        print("ConFix failed to generate plausible patch.")
        sys.exit(-63)

    # os.system("cd /home/aprweb/ ; "
    #         + "git diff "+target_dir+"/patches/0/"+perfect_faulty_path
    #                 + " "+target_dir+ "/" + perfect_faulty_path
    #                 + " > "+just_target+"/diff_file.txt")

    else:
        git_stream = os.popen("cd ~ ; "
                            + "git diff "+target_dir+ "/" + perfect_faulty_path
                                    + " "+target_dir+"/patches/0/"+perfect_faulty_path)

        foo = str(git_stream.read()).split('\n')

        os.system("echo \"diff --git a a\" > "+just_target+"/diff_file.txt")
        os.system("echo \"--- "+perfect_faulty_path+"\" >> "+just_target+"/diff_file.txt")
        os.system("echo \"+++ "+perfect_faulty_path+"\" >> "+just_target+"/diff_file.txt")
        
        for i in range(4, len(foo)):
            os.system("echo \""+foo[i]+"\" >> "+just_target+"/diff_file.txt")





    # # 패치의 path
    # /home/aprweb/APR_Projects/APR/target/Math/patches/0/org/apache/commons/math/stat/Frequency.java

    # # 주어지는 path
    # src/main/java/org/apache/commons/math/stat/Frequency.java




if __name__ == '__main__':
    main(sys.argv)
