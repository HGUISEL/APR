import sys

def main(args):
    execution_results = []
    bugs = dict()

    with open(args[1], 'r') as infile:
        execution_results = infile.read().splitlines()

    for each in execution_results:
        if each.startswith(('Total', 'Batch')):
            continue
        bug, result = each.split(':', 1)
        result = result.lstrip()

        if bug in bugs.keys():
            bugs[bug] += f' / {result}'
        else:
            bugs[bug] = result

    
    for bug in bugs.keys():
        print(f"{bug}: {bugs[bug]}")



if __name__ == '__main__':
    main(sys.argv)