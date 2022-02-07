import os
import sys

def main(args):
    root = os.getcwd()

    bug_info = []
    with open(f"{root}/{args[1]}", 'r') as infile:
        bug_info = infile.read().splitlines()

    identifiers = ['Chart', 'Closure', 'Lang', 'Math', 'Time', 'Mockito']
    csvfile = dict()
    for identifier in identifiers:
        csvfile[identifier] = open(f"{root}/pool/commit_collector/inputs/{identifier}.csv", "w")
        csvfile[identifier].write("Defects4J ID, Faulty file path, fix faulty line, blame faulty line\n")

    for bug in bug_info[1:]:
        identifier, line = bug.split(',', maxsplit = 1)
        csvfile[identifier].write(line + '\n')

    for identifier in identifiers:
        csvfile[identifier].close()


if __name__ == '__main__':
    main(sys.argv)