import pandas as pd
import csv
import os
import sys

project_name = sys.argv[1]

file_name = './output/tps/' + project_name + '.csv'
tps = pd.read_csv(file_name)

with open('./output/tps/' + project_name + '_hunk.csv', 'w', newline='') as csv_file:
    csv_writer = csv.writer(csv_file, delimiter=',')
    header = ['real_SHA', 'real_Path', 'real_hunk', 'Rank', 'Project', 'sugg_SHA', 'sugg_Path', 'sugg_hunk']

    csv_writer.writerow(header)

    for i in range(len(tps)):
        real_sha = tps[i][0]
        real_path = tps[i][1]
        rank = tps[i][2]
        project = tps[i][3]
        sugg_sha = tps[i][4]
        sugg_path = tps[i][5]

        stream_real = os.popen('cd ~/APR_Projects/data/AllBIC/reference/repositories/' + project_name + ' ; '
                               'git checkout ' + real_sha + ' ; '
                               'git diff ' + real_sha + '~ ' + real_path)

        real_hunk = str(stream_real.read())

        stream_sugg = os.popen('cd ~/APR_Projects/data/AllBIC/reference/repositories/' + project + ' ; '
                               'git checkout ' + sugg_sha + ' ; '
                               'git diff ' + sugg_sha + '~ ' + sugg_path)

        sugg_hunk = str(stream_sugg.read())

        if len(real_hunk) > 30000:
            real_hunk = 'change over 30K'
        if len(sugg_hunk) > 30000:
            sugg_hunk = 'change over 30K'

        line = [real_sha, real_path, real_hunk, rank, project, sugg_sha, sugg_path, sugg_hunk]
        csv_writer.writerow(line)
