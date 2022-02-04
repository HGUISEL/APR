import getopt
import sys
import os
import datetime as dt
import pandas as pd
import csv
import time

from colorama import Fore, Back, Style # Colorized output



def print_status(msg):
    print(Back.CYAN + Fore.BLACK + msg + Style.RESET_ALL)

def print_info(msg):
    print(Back.YELLOW + Fore.BLACK + msg + Style.RESET_ALL)

def print_complete(msg):
    print(Back.GREEN + Fore.WHITE + msg + Style.RESET_ALL)

def print_error(msg):
    print(Back.RED + Fore.WHITE + msg + Style.RESET_ALL)


def run_command(cmd):
    print_info(f'||| > Executing command "{cmd}"')
    print("\n" + "=" * 80 + "\n")
    start = dt.datetime.now()
    exit_code = os.system(cmd)
    end = dt.datetime.now()
    print("\n" + "=" * 80 + "\n")
    print_info(f"||| > Command exited with code {exit_code}")
    print_info(f"||| > Time taken : {(end - start)}")
    print("\n" + "=" * 80)

    return exit_code

class AbnormalExitException(Exception):
    pass

def launch(root, hash_id, identifier, bug_id):
    try:
        print_status("||| Step 3. Executing ConFix...")

        target_dir = f"{root}/target/{hash_id}_{identifier}-{bug_id}"
        output_dir = f"{target_dir}/outputs"

        just_target = target_dir


        # pwd is APR_Contents/APR/
        root = os.getcwd()
        # currently, we are running confix in APR directory


        perfect_info = pd.read_csv(f"{output_dir}/commit_collector/BFIC.csv", names=['Project','D4J ID','Faulty file path','faulty line','FIC sha','BFIC sha']).values[1]
        target_project, target_id, perfect_faulty_path, perfect_faulty_line = perfect_info[0], perfect_info[1], perfect_info[2], perfect_info[3]

        perfect_faulty_class, foo = perfect_faulty_path.split(".")
        perfect_faulty_class = perfect_faulty_class.replace("/", ".")

        target_dir = target_dir +"/"+ target_project

        ## prepare setup before running confix

        ### for D4J projects
        os.system(f"rm -rf {target_dir}; defects4j checkout -p {target_project} -v {target_id}b -w {target_dir}")
        os.system(f"cp {root}/confix/coverages{target_project.lower()}/{target_project.lower()}{target_id}b/coverage-info.obj {target_dir}")
        os.system(f"cd {target_dir}; {root}/confix/scripts/config.sh {target_project} {target_id} {perfect_faulty_class} {perfect_faulty_line}")
        os.system(f"cd {target_dir}; echo \"pool.source={just_target}/outputs/prepare_pool_source\" >> confix.properties; ")


        print("Configuration finished.")

        print("Executing ConFix...")
        os.system(f"cd {target_dir}; /usr/lib/jvm/java-8-openjdk-amd64/bin/java -Xmx4g -cp ../../../confix/lib/las.jar:../../../confix/lib/confix-ami_torun.jar -Duser.language=en -Duser.timezone=America/Los_Angeles com.github.thwak.confix.main.ConFix > log.txt")
        print("ConFix Execution Finished.")


        os.system("echo \"end\" >> "+ just_target+"/status.txt")


        if not os.path.isfile(f"{target_dir}/patches/0/{perfect_faulty_path}"):
            print("ConFix failed to generate plausible patch.")
            sys.exit(-63)

        else:
            git_stream = os.popen(f"cd ~; git diff {target_dir}/{perfect_faulty_path} {target_dir}/patches/0/{perfect_faulty_path}")

            foo = str(git_stream.read()).split('\n')

            os.system(f"echo \"diff --git a a\" > {just_target}/diff_file.txt")
            os.system(f"echo \"--- {perfect_faulty_path}\" >> {just_target}/diff_file.txt")
            os.system(f"echo \"+++ {perfect_faulty_path}\" >> {just_target}/diff_file.txt")
            
            for i in range(4, len(foo)):
                os.system(f"echo \"{foo[i]}\" >> {just_target}/diff_file.txt")
        executing_command = f"python3 ./confix/run_confix_web.py -d true -h {hash_id}"
        
        exit_code = run_command(executing_command)
        if exit_code != 0:
            raise AbnormalExitException

    except AbnormalExitException as e:
        pass


# def main(argv):
#     # Run Three steps: Commit Collector, Prepare Pool Source, and ConFix.


#     identifiers = ("Closure", "Lang", "Math", "Time")
#     timestamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')

#     hash_id = f'Batch_{timestamp}_{identifiers}'

#     # extract bug ids from here
#     # bug_id = None

#     # do a loop here:

#     whole_end = dt.datetime.now()
#     elapsed_time = (whole_end - whole_start)

#     print()
#     if exit_code != 0:
#         print_error("||| SimonFix failed to find out the patch, or aborted due to abnormal exit.")
#         print_info(f"||| Elapsed Time : {elapsed_time}")
#     else:
#         print_complete("||| SimonFix succeeded to find out the plausible patch.")
#         print_info(f"||| Elapsed Time : {elapsed_time}")
#         print_info("||| Generated Patch Content:")
#         print()
#         exit_code = os.system(f"cat ./target/{hash_id}/diff_file.txt")
#     print()
#     print("=" * 80)


def main(args):
    os.system("cd ./confix/ConFix-code; mvn clean package; cp target/confix-0.0.1-SNAPSHOT-jar-with-dependencies.jar /home/codemodel/hans/APR/confix/lib/confix-ami_torun.jar")

    bugs = []

    try:
        with open(args[2], 'r') as infile:
            bugs = infile.read().splitlines()
    except Exception as e: # Assume that the script failed to open the file.
        print(e)
        sys.exit(-1)

    hash_id = f'batch_{args[3]}'

    for bug in bugs:
        identifier, bug_id = bug.split('_')

        while True:
            if os.path.isdir(f"{args[1]}/target/batch_{args[2]}_{identifier}-{bug_id}"):
                launch(args[1], hash_id, identifier, bug_id)
                break
            else:
                time.sleep(1)

if __name__ == '__main__':
    main(sys.argv)


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
