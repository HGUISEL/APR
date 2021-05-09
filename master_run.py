import os #nope
import sys

def main(argv):

    #### prepare the pool ###
    os.system("python3 ./pool/runner_web/commit_collector_web.py -d true")
    os.system("python3 ./pool/runner_web/change_vector_collector_web.py -d true")
    os.system("python3 ./pool/simfin/gv_ae.py -p test -k 10") # -p means predict, -t means train; -k is for top-k neighbors
    os.system("python3 ./pool/runner_web/prepare_pool_source_web.py")


if __name__ == '__main__':
    main(sys.argv)