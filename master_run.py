import os #nope
import sys

def main(argv):

    #### prepare the pool ###
    os.system("python3 ./pool/runner_web/commit_collector_web.py -d true -h 1000 -i Closure-14")
    os.system("python3 ./pool/runner_web/change_vector_collector_web.py -d true -h 1000")
    os.system("python3 ./pool/simfin/gv_ae_web.py -p test -k 10 -h 1000") # -p means predict, -t means train; -k is for top-k neighbors
    os.system("python3 ./pool/runner_web/prepare_pool_source_web.py -h 1000")

    os.system("python3 ./confix/run_confix_web.py -d true -h 1000")

    


if __name__ == '__main__':
    main(sys.argv)