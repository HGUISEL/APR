import numpy as np
import getopt
import pandas as pd
import sys

CUTOFF = 0
K_NEIGHBORS = 1


def evaluate():
    return


def write_recall(pred_list):
    cutoffs = str(CUTOFF).split(',')
    min_cutoff = float(cutoffs[0])
    max_cutoff = float(cutoffs[1])

    cutoff_idx = 0
    cutoff = min_cutoff
    while cutoff < max_cutoff:
        try:
            for i in range(1, K_NEIGHBORS + 1):
                for j in range(len(pred_list)):
                    if pred_list[j][6] <= i:
                        pred_list[cutoff_idx][i][j] += (pred_list[j][7] / j)
                    else:
                        continue
        finally:
            cutoff += 0.1
            cutoff_idx += 1
    return


def write_precision(pred_list):
    return


def write_f_measure(pred_list):
    return


def main(argv):
    global CUTOFF
    global K_NEIGHBORS
    test_name = ''
    try:
        opts, args = getopt.getopt(argv[1:], "hk:c:p:", ["help", "k_neighbors", "cutoff", "predict"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)

    for o, a in opts:
        if o in ("-h", "--help"):
            print("")
            sys.exit()
        elif o in ("-k", "--k_neighbors"):
            K_NEIGHBORS = int(a)
        elif o in ("-c", "--cutoff"):
            CUTOFF = a
        elif o in ("-p", "--predict"):
            test_name = a
        else:
            assert False, "unhandled option"

    result_file = './output/eval/' + test_name + '_result.csv'
    results_csv = pd.read_csv(result_file, names=['Y_BIC_SHA', 'Y_BIC_Path', 'Y_BIC_Hunk',
                                                  'Y_BFC_SHA', 'Y_BFC_Path', 'Y_BFC_Hunk',
                                                  'Rank', 'Sim-Score', 'BI_lines', 'Label',
                                                  'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BIC_Hunk',
                                                  'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk']).values
    results_len = int((len(results_csv) - 1) / K_NEIGHBORS)

    hash_map = {}

    for i in range(1, results_len):
        sha_bic = results_csv[i][3]
        path_bic = results_csv[i][1]

        if i % K_NEIGHBORS == 0:
            hash_map[path_bic+sha_bic] = []

    evaluate()

if __name__ == '__main__':
    main(sys.argv)
