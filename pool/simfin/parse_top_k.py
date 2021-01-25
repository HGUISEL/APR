import csv
import os
import pandas as pd
import sys


def main(argv):
    K_NEIGHBORS = int(argv[1])
    test_name = argv[2]
    file_path = '/data/jihoshin/'

    Y_test = pd.read_csv('./output/testset/Y_' + test_name + '.csv',
                         names=['index',
                                'path_BBIC',
                                'path_BIC',
                                'sha_BBIC',
                                'sha_BIC',
                                'path_BBFC',
                                'path_BFC',
                                'sha_BBFC',
                                'sha_BFC',
                                'key',
                                'project',
                                'label']).values
    Y_train = pd.read_csv('./output/trainset/Y_no_test_all.csv',
                          names=['index',
                                 'path_BBIC',
                                 'path_BIC',
                                 'sha_BBIC',
                                 'sha_BIC',
                                 'path_BBFC',
                                 'path_BFC',
                                 'sha_BBFC',
                                 'sha_BFC',
                                 'key',
                                 'project',
                                 'label']).values

    test_num = len([name for name in os.listdir(file_path + test_name + '/')])
    print('num of test:', test_num)

    for i in range(test_num):
        test_path = file_path + test_name + '/test' + str(i)
        top_k = dict()
        dist = pd.read_csv(test_path + '/dist.csv').values
        for j in range(len(dist)):
            test_list = list(top_k)
            if j != 0:
                last_key = test_list[-1]
            if j < K_NEIGHBORS:
                top_k[str(j)] = dist[j]
                top_k = dict(sorted(top_k.items(), key=lambda x: x[1]))
            elif top_k[str(last_key)] > dist[j]:
                top_k.pop(last_key)
                top_k[str(j)] = dist[j]
                top_k = dict(sorted(top_k.items(), key=lambda x: x[1]))
        result_file = file_path + 'result.csv'
        with open(test_path + '/result.csv', 'w', newline='') as result_csv:
            csv_writer = csv.writer(result_csv, delimiter=',')
            keys = list(top_k)
            vals = list(top_k.values())

            header = ['Y_BIC_SHA', 'Y_BIC_Path', 'Y_BIC_Hunk',
                      'Y_BFC_SHA', 'Y_BFC_Path', 'Y_BFC_Hunk', 'Y_label',
                      'Rank', 'Dist', 'Project',
                      'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BIC_Hunk',
                      'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk', 'Y^_label']
            csv_writer.writerow(header)

            count = 0
            for key in keys:
                key = int(key)
                y_bic_sha = str(Y_test[i][4])
                y_bic_path = str(Y_test[i][2])
                y_bfc_sha = str(Y_test[i][8])
                y_bfc_path = str(Y_test[i][6])
                y_label = str(Y_test[i][11])
                y_bic_hunk = '-'
                y_bfc_hunk = '-'

                yhat_bic_sha = str(Y_train[key][4])
                yhat_bic_path = str(Y_train[key][2])
                yhat_bfc_sha = str(Y_train[key][8])
                yhat_bfc_path = str(Y_train[key][6])
                yhat_project = str(Y_train[key][10])
                yhat_label = str(Y_train[key][11])
                yhat_bic_hunk = '-'
                yhat_bfc_hunk = '-'

                instance = [y_bic_sha, y_bic_path, y_bic_hunk,
                            y_bfc_sha, y_bfc_path, y_bfc_hunk, y_label,
                            count + 1, float(vals[count]), yhat_project,
                            yhat_bic_sha, yhat_bic_path, yhat_bic_hunk,
                            yhat_bfc_sha, yhat_bfc_path, yhat_bfc_hunk, yhat_label]

                csv_writer.writerow(instance)
                count += 1
        print('test', i, 'done!')


if __name__ == '__main__':
    main(sys.argv)
