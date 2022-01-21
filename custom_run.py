import getopt
import sys
import os
import datetime as dt
from colorama import Fore, Back, Style

def print_status(msg):
    print(Back.CYAN + Fore.BLACK + msg + Style.RESET_ALL)

def print_info(msg):
    print(Back.YELLOW + Fore.BLACK + msg + Style.RESET_ALL)

def print_complete(msg):
    print(Back.GREEN + Fore.WHITE + msg + Style.RESET_ALL)

def print_error(msg):
    print(Back.RED + Fore.WHITE + msg + Style.RESET_ALL)

def main(argv):
    # Parse arguments given
    try:
        opts, args = getopt.getopt(argv[1:], "", ["github-repository=", "commit-id=", "faulty-line=", "faulty-file=", "source-path=", "target-path=", "test-list=", "test-target-path=", "compile-target-path=", "build-tool=", "case-file="])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(-1)
    
    case = {'github_repository' : None, 'commit_id' : None, 'faulty_line' : None, 'faulty_file' : None, 'source_path' : None, 'target_path' : None, 'test_list' : None, 'test_target_path' : None, 'compile_target_path' : None, 'build_tool' : None}

    for option, argument in opts:
        if option == "--github-repository":
            case['github_repository'] = argument

        elif option == "--commit-id":
            case['commit_id'] = argument

        elif option == "--faulty-line":
            case['faulty_line'] = argument

        elif option == "--faulty-file":
            case['faulty_file'] = argument

        elif option == "--source-path":
            case['source_path'] = argument

        elif option == "--target-path":
            case['target_path'] = argument

        elif option == "--test-list":
            case['test_list'] = argument

        elif option == "--test-target-path":
            case['test_target_path'] = argument

        elif option == "--compile-target-path":
            case['compile_target_path'] = argument

        elif option == "--build-tool":
            case['build_tool'] = argument
        
        elif option == "--case-file":
            with open(argument, 'r') as f:
                file_lines = f.readlines()
                for i, each in enumerate(case.keys()):
                    case[each] = file_lines[i].strip()

    case['repository_name'] = case['github_repository'].rsplit('/', 1)[-1]

    input_validity = True

    for input_argument in case.keys():
        if case[input_argument] == None:
            input_validity = False
            print_error(f"{input_argument} is not valid.")

    if input_validity == False:
        print_error("Error : All arguments should given except case-file.")
        print("You can also give file as an argument to 'case-file' option, but the file should contain all arguments required in order:")
        for each in case.keys():
            if each == 'repository_name':
                pass
            print(f"- {each.replace('_', '-')}")
        print()
        print("Terminating program.")
        sys.exit(-1)

    print("Processed arguments are as follows:")
    for argument in case.keys():
        print(f'- {argument} : {case[argument]}')
    
    print()
    print("Do you wish to continue with those arguments? (Y/n) : ", end = '')
    answer = input()

    if answer not in ['', 'Y', 'y']:
        exit(1)

    case['hash_id'] = abs(hash(f"{case['repository_name']}-{dt.datetime.now().strftime('%Y%m%d%H%M%S')}"))
    print_info(f"Hash ID generated as {case['hash_id']}. Find byproducts in ./target/{case['hash_id']}")

    # Run SimonFix Engine from those orders
    
    print()
    print("=" * 80)

    #### prepare the pool ###
    print()
    print_status("||| Executing Commit Collector...")
    print()
    return_code = os.system(f"python3 ./pool/runner_web/commit_collector_web.py -h {case['hash_id']} -i {case['repository_name']},{case['faulty_file']},{case['faulty_line']},{case['commit_id']},{case['github_repository']}")
    print()
    print_status(f"||| > Commit Collector exited with code {return_code}")
    print()
    print("=" * 80)

    print()
    print_status("||| Executing Change Vector Collector...")
    print()
    return_code = os.system(f"python3 ./pool/runner_web/change_vector_collector_web.py -h {case['hash_id']}")
    print()
    print_status(f"||| > Change Vector Collector exited with code {return_code}")
    print()
    print("=" * 80)

    print()
    print_status("||| Executing SimFin...")
    print()
    return_code = os.system(f"python3 ./pool/simfin/gv_ae_web.py -p test -k 10 -h {case['hash_id']}") # -p means predict, -t means train; -k is for top-k neighbors
    print()
    print_status(f"||| > SimFin exited with code {return_code}")
    print()
    print("=" * 80)

    print()
    print_status("||| Preparing pool source...")
    print()
    #os.system('su aprweb -c "python3 /home/codemodel/hans/APR/pool/runner_web/prepare_pool_source_web.py -h 1000"')
    return_code = os.system(f"python3 ./pool/runner_web/prepare_pool_source_web.py -h {case['hash_id']}")
    print()
    print_status(f"||| > Pool Source preparation exited with code {return_code}")
    print()
    print("=" * 80)


    print()
    print_status("||| Executing ConFix...")
    print()
    return_code = os.system(f"python3 ./confix/run_confix_web.py -h {case['hash_id']} -i {case['source_path']},{case['target_path']},{case['test_list']},{case['test_target_path']},{case['compile_target_path']},{case['build_tool']}")
    print()
    print_status(f"||| > ConFix exited with code {return_code}")
    print()
    print("=" * 80)

    print()
    if return_code != 0:
        print_error("||| SimonFix failed to find out the patch.")
    else:
        print_complete("||| SimonFix failed to find out the patch.")
        print("||| Generated Patch Content:")
        print()
        return_code = os.system(f"cat ./target/{case['hash_id']}/diff_file.txt")
    print()
    print("=" * 80)

    


if __name__ == '__main__':
    main(sys.argv)
