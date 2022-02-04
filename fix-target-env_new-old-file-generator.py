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
        target_dir = f"{root}/target/{hash_id}_{identifier}-{bug_id}"

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

    except AbnormalExitException as e:
        pass


def main(args):
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
        prepare(args[1], hash_id, identifier, bug_id)

if __name__ == '__main__':
    main(sys.argv)
