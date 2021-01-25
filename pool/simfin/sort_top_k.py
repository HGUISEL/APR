import csv
import os
import pandas as pd
import sys


def main(argv):
    file_path = '/data/jihoshin/' + argv[1]

    # dist_csv = pd.read_csv(file_path)
    test_num = len([name for name in os.listdir(file_path)]) - 1
    print('test instance:', test_num)

    for i in range(test_num):
        test_path = file_path + '/test' + str(i)
        dist_i = pd.read_csv(test_path + '/dist.csv', header=None).values
        test_dict = dict()
        # print('train instances:', len(dist_i))
        for j in range(len(dist_i)):
            test_dict[str(j)] = dist_i[j]
        sorted_test = sorted(test_dict.items(), key=lambda item: item[1])
        with open(test_path + '/sorted.csv', 'w', newline='') as sorted_csv:
            csv_writer = csv.writer(sorted_csv, delimiter=',')
            for (key, value) in sorted_test:
                value = float(value)
                row = [value, key]
                csv_writer.writerow(row)
        print('test', i, 'done!')


if __name__ == '__main__':
    main(sys.argv)
