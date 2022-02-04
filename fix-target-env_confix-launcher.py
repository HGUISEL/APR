import getopt
import sys
import os
import datetime as dt
import pandas as pd
import csv

from colorama import Fore, Back, Style # Colorized output



def print_status(msg):
    print(Back.CYAN + Fore.BLACK + msg + Style.RESET_ALL)

def print_info(msg):
    print(Back.YELLOW + Fore.BLACK + msg + Style.RESET_ALL)

def print_complete(msg):
    print(Back.GREEN + Fore.WHITE + msg + Style.RESET_ALL)

def print_error(msg):
    print(Back.RED + Fore.WHITE + msg + Style.RESET_ALL)

def print_help(cmd):
    options_list = ['repository', 'commit_id', 'faulty_file', 'faulty_line', 'source_path', 'target_path', 'test_list', 'test_target_path', 'compile_target_path', 'build_tool']

    print("Usage :")
    print(f"- Defects4J check         : {cmd} --defects4j <Identifier>-<Bug-ID>")
    print(f"- Custom repository check : {cmd} --repository <github_repository> --commit_id <commit_id> --faulty_file <path_from_project_root> --faulty_line <buggy_line_number> --source_path <source_path> --target_path <target_path> --test_list <test_class_name> --test_target_path <test_target_path> --compile_target_path <compile_target_path> --build_tool <maven_or_gradle>")
    print(f"- Custom repository check : {cmd} --case_file <file_containing_arguments>")
    print()
    print("When bringing arguments from file, it should contain arguments in the following order:")
    for option in options_list:
        print(f"- {option}")
    print()

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

def prepare(root, hash_id, identifier, bug_id):
    try:
        target_dir = f"{root}/target/{hash_id}"

        executing_command = f"python3 ./pool/runner_web/commit_collector_web.py -d true -h {hash_id} -i {identifier}-{bug_id}"
        print_status("||| Prep.Step 1. Launching Commit Collector...")
        
        exit_code = run_command(executing_command)
        if exit_code != 0:
            raise AbnormalExitException


        print_status("||| Prep.Step 2. Manually preparing pool source for ConFix...")
        print("\n" + "=" * 80 + "\n")

        try:
            bfic = pd.read_csv(f'{target_dir}/commit_collector/BFIC.csv', names = ['Project', 'D4J ID', 'Faulty file path', 'Faulty line', 'FIC_sha', 'BFIC_sha']).values[1]
            assert os.system(f"cd {target_dir}; defects4j checkout -p {identifier} -v {bug_id}b -w buggy") == 0, "checkout for buggy project failed"
            assert os.system(f"cd {target_dir}; defects4j checkout -p {identifier} -v {bug_id}f -w fixed") == 0, "checkout for fixed project failed"

            assert os.system(f"cd {target_dir}; mkdir -p outputs; mkdir -p outputs/prepare_pool_source") == 0, "what?"
            assert os.system(f"cp {target_dir}/buggy/{bfic['Faulty file path']} {target_dir}/outputs/prepare_pool_source/{identifier}_rank-1_old.java") == 0, "copying buggy file failed"
            assert os.system(f"cp {target_dir}/fixed/{bfic['Faulty file path']} {target_dir}/outputs/prepare_pool_source/{identifier}_rank-1_new.java") == 0, "copying fixed file failed"
        except AssertionError as e:
            print(e)
            raise AbnormalExitException

        print("\n" + "=" * 80 + "\n")

        executing_command = f"python3 ./confix/run_confix_web.py -d true -h {hash_id}"
        print_status("||| Step 3. Executing ConFix...")
        
        exit_code = run_command(executing_command)
        if exit_code != 0:
            raise AbnormalExitException

    except AbnormalExitException as e:
        pass

def launch(root, hash_id, identifier, bug_id):
    try:
        target_dir = f"{root}/target/{hash_id}"

        executing_command = f"python3 ./pool/runner_web/commit_collector_web.py -d true -h {hash_id} -i {identifier}-{bug_id}"
        print_status("||| Step 1. Launching Commit Collector...")
        
        exit_code = run_command(executing_command)
        if exit_code != 0:
            raise AbnormalExitException


        print_status("||| Step 2. Manually preparing pool source for ConFix...")
        print("\n" + "=" * 80 + "\n")

        try:
            bfic = pd.read_csv(f'{target_dir}/commit_collector/BFIC.csv', names = ['Project', 'D4J ID', 'Faulty file path', 'Faulty line', 'FIC_sha', 'BFIC_sha']).values[1]
            assert os.system(f"cd {target_dir}; defects4j checkout -p {identifier} -v {bug_id}b -w buggy") == 0, "checkout for buggy project failed"
            assert os.system(f"cd {target_dir}; defects4j checkout -p {identifier} -v {bug_id}f -w fixed") == 0, "checkout for fixed project failed"

            assert os.system(f"cd {target_dir}; mkdir -p outputs; mkdir -p outputs/prepare_pool_source") == 0, "what?"
            assert os.system(f"cp {target_dir}/buggy/{bfic['Faulty file path']} {target_dir}/outputs/prepare_pool_source/{identifier}_rank-1_old.java") == 0, "copying buggy file failed"
            assert os.system(f"cp {target_dir}/fixed/{bfic['Faulty file path']} {target_dir}/outputs/prepare_pool_source/{identifier}_rank-1_new.java") == 0, "copying fixed file failed"
        except AssertionError as e:
            print(e)
            raise AbnormalExitException

        print("\n" + "=" * 80 + "\n")

        executing_command = f"python3 ./confix/run_confix_web.py -d true -h {hash_id}"
        print_status("||| Step 3. Executing ConFix...")
        
        exit_code = run_command(executing_command)
        if exit_code != 0:
            raise AbnormalExitException

    except AbnormalExitException as e:
        pass

def main(argv):
    # Run Three steps: Commit Collector, Prepare Pool Source, and ConFix.


    identifiers = ("Closure", "Lang", "Math", "Time")
    timestamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')

    hash_id = f'Batch_{timestamp}_{identifiers}'

    # extract bug ids from here
    # bug_id = None

    # do a loop here:

    whole_end = dt.datetime.now()
    elapsed_time = (whole_end - whole_start)

    print()
    if exit_code != 0:
        print_error("||| SimonFix failed to find out the patch, or aborted due to abnormal exit.")
        print_info(f"||| Elapsed Time : {elapsed_time}")
    else:
        print_complete("||| SimonFix succeeded to find out the plausible patch.")
        print_info(f"||| Elapsed Time : {elapsed_time}")
        print_info("||| Generated Patch Content:")
        print()
        exit_code = os.system(f"cat ./target/{hash_id}/diff_file.txt")
    print()
    print("=" * 80)

    


if __name__ == '__main__':
    main(sys.argv)
