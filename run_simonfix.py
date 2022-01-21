import getopt
import sys
import os
import datetime as dt

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
    print_info(f'||| Executing command "{cmd}"')
    print()
    print("=" * 80)
    print()
    exit_code = os.system(cmd)
    print()
    print("=" * 80)
    print()
    print_info(f"||| > Command exited with code {exit_code}")
    print()
    print("=" * 80)

    return exit_code


def main(argv):
    # Parse arguments given
    options_list = ['repository', 'commit_id', 'faulty_file', 'faulty_line', 'source_path', 'target_path', 'test_list', 'test_target_path', 'compile_target_path', 'build_tool']
    try:
        opts, args = getopt.getopt(argv[1:], "", [option + '=' for option in options_list] + ['help', 'defects4j=', 'case_file='])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(-1)
    is_test = False
    case = dict() # {'repository' : None, 'commit_id' : None, 'faulty_file' : None, 'faulty_line' : None, 'source_path' : None, 'target_path' : None, 'test_list' : None, 'test_target_path' : None, 'compile_target_path' : None, 'build_tool' : None, 'hash' : None}
    case['is_defects4j'] = False

    if len(args) != 0:
        print(f"Arguments {args} ignored.")

    for option, argument in opts:
        if option == '--help':
            print_help(argv[0])
            sys.exit(0)

        elif option == '--defects4j':
            case['is_defects4j'] = True
            case['repository'] = argument

        elif option == "--repository":
            case['repository'] = argument

        elif option == "--commit_id":
            case['commit_id'] = argument

        elif option == "--faulty_file":
            case['faulty_file'] = argument

        elif option == "--faulty_line":
            case['faulty_line'] = argument

        elif option == "--source_path":
            case['source_path'] = argument

        elif option == "--target_path":
            case['target_path'] = argument

        elif option == "--test_list":
            case['test_list'] = argument

        elif option == "--test_target_path":
            case['test_target_path'] = argument

        elif option == "--compile_target_path":
            case['compile_target_path'] = argument

        elif option == "--build_tool":
            case['build_tool'] = argument
        
        elif option == "--case_file":
            with open(argument, 'r') as f:
                file_lines = f.readlines()
                for i, each in enumerate(options_list):
                    case[each] = file_lines[i].strip()
        elif option == "--test":
            is_test = True
            case['hash_id'] = argument

    case['project_name'] = case['repository'].rsplit('/', 1)[-1]

    options_list = ['repository', 'project_name', 'is_defects4j'] if case['is_defects4j'] == True else options_list + ['is_defects4j']
    input_validity = True

    print("Processed arguments are as follows:")
    for argument in options_list:
        print(f"- {argument} : {case[argument]}")

        if case.get(argument, None) == None:
            input_validity = False
            print_error(f"{argument} is not valid.")

    if input_validity == False:
        print_error("Error : All arguments should given except case_file.")
        print("You can also give file as an argument to 'case_file' option, but the file should contain all arguments required in order:")
        for each in case.keys():
            if each == 'project_name':
                pass
            print(f"- {each.replace('_', '-')}")
        print()
        print("Terminating program.")
        sys.exit(-1)
    
    print()
    print("Do you wish to continue with those arguments? (Y/n) : ", end = '')
    answer = input()

    if answer not in ['', 'Y', 'y']:
        exit(1)

    if not is_test:
        case['hash_id'] = abs(hash(f"{case['project_name']}-{dt.datetime.now().strftime('%Y%m%d%H%M%S')}"))
    print_info(f"Hash ID generated as {case['hash_id']}. Find byproducts in ./target/{case['hash_id']}")

    # Run SimonFix Engine from those orders
    
    print()
    print("=" * 80)
    exit_code = 0
    executing_command = ""

    whole_start = dt.datetime.now()

    for i in range(1): # no loop, used to activate 'break'
        print()

        # Commit Collector
        
        if case['is_defects4j'] == True:
            executing_command = f"python3 ./pool/runner_web/commit_collector_web.py -d true -h {case['hash_id']} -i {case['project_name']}"
        else:
            executing_command = f"python3 ./pool/runner_web/commit_collector_web.py -h {case['hash_id']} -i {case['project_name']},{case['faulty_file']},{case['faulty_line']},{case['commit_id']},{case['repository']}"
        print_status("||| Step 1. Launching Commit Collector...")
        start = dt.datetime.now()
        exit_code = run_command(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

        if exit_code != 0:
            break

        print()

        # Change Vector Collector
        
        if case['is_defects4j'] == True:
            executing_command = f"python3 ./pool/runner_web/change_vector_collector_web.py -d true -h {case['hash_id']}"
        else:
            executing_command = f"python3 ./pool/runner_web/change_vector_collector_web.py -h {case['hash_id']}"
        print_status("||| Step 2. Launching Change Vector Collector...")
        start = dt.datetime.now()
        exit_code = run_command(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

        if exit_code != 0:
            break

        print()

        # SimFin

        executing_command = f"python3 ./pool/simfin/gv_ae_web.py -p test -k 10 -h {case['hash_id']}" # -p means predict, -t means train; -k is for top-k neighbors
        print_status("||| Step 3. Launching SimFin...")
        start = dt.datetime.now()
        exit_code = run_command(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

        if exit_code != 0:
            break

        print()

        # Prepare Pool Source

        executing_command = f"python3 ./pool/runner_web/prepare_pool_source_web.py -h {case['hash_id']}"
        print_status("||| Step 4. Preparing pool source for ConFix...")
        start = dt.datetime.now()
        exit_code = os.system(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

        if exit_code != 0:
            break

        print()

        # ConFix

        if case['is_defects4j'] == True:
            executing_command = f"python3 ./confix/run_confix_web.py -d true -h {case['hash_id']}"
        else:
            executing_command = f"python3 ./confix/run_confix_web.py -h {case['hash_id']} -i {case['source_path']},{case['target_path']},{case['test_list']},{case['test_target_path']},{case['compile_target_path']},{case['build_tool']}"
        print_status("||| Step 5. Executing ConFix...")
        start = dt.datetime.now()
        exit_code = os.system(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

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
        exit_code = os.system(f"cat ./target/{case['hash_id']}/diff_file.txt")
    print()
    print("=" * 80)

    


if __name__ == '__main__':
    main(sys.argv)
