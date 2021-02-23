import os #nope
import sys

def main(argv):
    os.system("python3 ./pool/runner/commit_collector.py")
    os.system("python3 ./pool/runner/change_vector_collector.py")
    os.system("python3 ./pool/simfin/gv_ae.py -p test -k 10")
    os.system("python3 ./pool/runner/las.py")


if __name__ == '__main__':
    main(sys.argv)