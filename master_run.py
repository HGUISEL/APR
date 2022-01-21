import os #nope
import sys

def main(argv):

    print("=" * 80)

    #### prepare the pool ###
    print()
    print("||| Executing Commit Collector...")
    print()
    os.system("python3 ./pool/runner_web/commit_collector_web.py -d true -h 1000 -i Closure-14")
    print("=" * 80)

    print()
    print("||| Executing Change Vector Collector...")
    print()
    os.system("python3 ./pool/runner_web/change_vector_collector_web.py -d true -h 1000")
    print()
    print("=" * 80)

    print()
    print("||| Executing SimFin...")
    print()
    os.system("python3 ./pool/simfin/gv_ae_web.py -p test -k 10 -h 1000") # -p means predict, -t means train; -k is for top-k neighbors
    print()
    print("=" * 80)

    print()
    print("||| Preparing pool source...")
    print()
    #os.system('su aprweb -c "python3 /home/codemodel/hans/APR/pool/runner_web/prepare_pool_source_web.py -h 1000"')
    os.system('python3 ./pool/runner_web/prepare_pool_source_web.py -h 1000')
    print()
    print("=" * 80)


    print()
    print("||| Executing ConFix...")
    print()
    os.system("python3 ./confix/run_confix_web.py -d true -h 1000")
    print()
    print("=" * 80)


    print()
    print("||| Generated Patch Content:")
    print()
    os.system("cat ./target/1000/diff_file.txt")
    print()
    print("=" * 80)

    


if __name__ == '__main__':
    main(sys.argv)
