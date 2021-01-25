import pandas as pd
import csv
import sys

project_name = sys.argv[1]

file_name = '/Users/jihoshin/Desktop/hunk/' + project_name + '_hunk.csv'
hunk_csv = pd.read_csv(file_name, encoding='CP949').values

with open('/Users/jihoshin/Desktop/hunk/out/' + project_name + '_filtered.csv', 'w', newline='') as out_file:
    csv_writer = csv.writer(out_file, delimiter=',')
    header = ['real_SHA', 'real_Path', 'real_hunk', 'Rank', 'Project', 'sugg_SHA', 'sugg_Path', 'sugg_hunk']

    csv_writer.writerow(header)

    for i in range(len(hunk_csv)):
        real_sha = hunk_csv[i][0]
        real_path = hunk_csv[i][1]
        real_hunk = str(hunk_csv[i][2])
        rank = hunk_csv[i][3]
        project = hunk_csv[i][4]
        sugg_sha = hunk_csv[i][5]
        sugg_path = hunk_csv[i][6]
        sugg_hunk = str(hunk_csv[i][7])

        if len(real_hunk.split('\n')) > 15:
            continue

        if len(real_hunk.split('\n')) < 2:
            continue

        if len(sugg_hunk.split('\n')) < 2:
            continue

        line = [real_sha, real_path, real_hunk, rank, project, sugg_sha, sugg_path, sugg_hunk]
        csv_writer.writerow(line)
