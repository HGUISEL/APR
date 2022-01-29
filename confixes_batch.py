import getopt
import sys
import os
import datetime as dt
import pandas as pd
import csv

from colorama import Fore, Back, Style # Colorized output



def print_status(msg):
    print(Back.CYAN + Fore.BLACK + msg + Style.RESET_ALL)

def print_info(msg):
    print(Back.YELLOW + Fore.BLACK + msg + Style.RESET_ALL)

def print_complete(msg):
    print(Back.GREEN + Fore.WHITE + msg + Style.RESET_ALL)

def print_error(msg):
    print(Back.RED + Fore.WHITE + msg + Style.RESET_ALL)

def print_help(cmd):
    options_list = ['repository', 'commit_id', 'faulty_file', 'faulty_line', 'source_path', 'target_path', 'test_list', 'test_target_path', 'compile_target_path', 'build_tool']

    print("Usage :")
    print(f"- Defects4J check         : {cmd} --defects4j <Identifier>-<Bug-ID>")
    print(f"- Custom repository check : {cmd} --repository <github_repository> --commit_id <commit_id> --faulty_file <path_from_project_root> --faulty_line <buggy_line_number> --source_path <source_path> --target_path <target_path> --test_list <test_class_name> --test_target_path <test_target_path> --compile_target_path <compile_target_path> --build_tool <maven_or_gradle>")
    print(f"- Custom repository check : {cmd} --case_file <file_containing_arguments>")
    print()
    print("When bringing arguments from file, it should contain arguments in the following order:")
    for option in options_list:
        print(f"- {option}")
    print()

def run_command(cmd):
    print_info(f'||| Executing command "{cmd}"')
    print()
    print("=" * 80)
    print()
    exit_code = os.system(cmd)
    print()
    print("=" * 80)
    print()
    print_info(f"||| > Command exited with code {exit_code}")
    print()
    print("=" * 80)

    return exit_code


def main(argv):
    # Run Three steps: Commit Collector, Prepare Pool Source, and ConFix.


    identifiers = ("Closure", "Lang", "Math", "Time")
    timestamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')

    hash_id = f'Batch_{timestamp}_{identifiers}'

    # extract bug ids from here
    # bug_id = None

    # do a loop here:

    identifier = None
    bug_id = None
    target = f'{identifier}-{bug_id}'

    executing_command = f"python3 ./pool/runner_web/commit_collector_web.py -d true -h {hash_id} -i {target}"
    print_status("||| Step 1. Launching Commit Collector...")
    start = dt.datetime.now()
    exit_code = run_command(executing_command)
    end = dt.datetime.now()
    print_info(f"||| Time taken : {(end - start)}")

    if exit_code != 0:
        break

    # Y_BIC_SHA,Y_BIC_Path,Y_Project,Y_BugId,Rank,Sim-Score,Y^_Project,Y^_BIC_SHA,Y^_BIC_Path,Y^_BFC_SHA,Y^_BFC_Path,Y^_BFC_Hunk
    # -,-,-,-,1,0.02988082148846013,lucene-solr,35f1044b8db5cb7a793df0db616a9a9478a95b88,lucene/misc/src/java/org/apache/lucene/misc/HighFreqTerms.java,18b1c94eb3f888da13b7ddf6f556c9a93f89fd01,lucene/misc/src/java/org/apache/lucene/misc/HighFreqTerms.java,-
    # -,-,-,-,2,0.02988082148846013,lucene-solr,c039e210b0f7c024586cb8dcacf54feac820ac5d,solr/src/java/org/apache/solr/search/SolrIndexSearcher.java,3b6da22aa73a6f5df3bc95abfb56baafc193ce89,solr/core/src/java/org/apache/solr/search/SolrIndexSearcher.java,-
    # -,-,-,-,3,0.02988082148846013,mahout,d61a0ee216389fcac1d3e56f531aa4cc8f597c59,utils/src/main/java/org/apache/mahout/clustering/evaluation/ClusterEvaluator.java,22b0c3d3ca7fb07f5abf34aab6ad3449d5b69c7b,utils/src/main/java/org/apache/mahout/clustering/evaluation/ClusterEvaluator.java,-
    # -,-,-,-,4,0.02988082148846013,asterixdb,80a614827adb89953326d65e38563d0318a2e79c,asterix-runtime/src/main/java/edu/uci/ics/asterix/runtime/evaluators/functions/NumericMultiplyDescriptor.java,89bbc27260c370cbf9f795294f20e18abd24ada6,asterix-runtime/src/main/java/edu/uci/ics/asterix/runtime/evaluators/functions/NumericMultiplyDescriptor.java,-
    # -,-,-,-,5,0.02988082148846013,kafka,32e97b1d9db46cab526d1882eaf9633934ed21bd,streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBSegmentedBytesStore.java,08fe24b46a327424e3249eeef1a32e05db717b18,streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBSegmentedBytesStore.java,-
    # -,-,-,-,6,0.02988082148846013,cassandra,b95a49c5f9e22225fc0ebc703fd62bd8241bd9d7,src/java/org/apache/cassandra/db/Row.java,e029b7d0c11f32ba0c1647778759924bffd2275a,src/java/org/apache/cassandra/db/Row.java,-
    # -,-,-,-,7,0.02988082148846013,accumulo,ac451b382ad38003788e37496c003dcf9574f03a,start/src/main/java/org/apache/accumulo/start/classloader/vfs/AccumuloReloadingVFSClassLoader.java,40b41f26ed65ccc9ea9028d664505a4a875e9bd4,start/src/main/java/org/apache/accumulo/start/classloader/vfs/AccumuloReloadingVFSClassLoader.java,-
    # -,-,-,-,8,0.02988082148846013,activemq-artemis,a4beb18a6ec02c8ec06eb6e224def16cab43726f,artemis-protocols/artemis-amqp-protocol/src/main/java/org/apache/activemq/artemis/protocol/amqp/broker/AMQPSessionCallback.java,f874a02d17eb7787410c69024397353e53fc7689,artemis-protocols/artemis-amqp-protocol/src/main/java/org/apache/activemq/artemis/protocol/amqp/broker/AMQPSessionCallback.java,-
    # -,-,-,-,9,0.02988082148846013,hive,980291bb90386310ba5961b1673755742bf9e558,ql/src/java/org/apache/hadoop/hive/ql/optimizer/optiq/translator/JoinTypeCheckCtx.java,9559306c3698a453609fe1ea47fddf219ca397b3,ql/src/java/org/apache/hadoop/hive/ql/optimizer/calcite/translator/JoinTypeCheckCtx.java,-
    # -,-,-,-,10,0.02988082148846013,ignite,42ea30da4385e321c5fe904925eb3e3ac3c555cc,modules/core/src/main/java/org/apache/ignite/internal/binary/builder/BinaryObjectBuilderImpl.java,34635a4409aa7659317ab9a82f8761dd1a3c98b2,modules/core/src/main/java/org/apache/ignite/internal/binary/builder/BinaryObjectBuilderImpl.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,1,3.618230547143233e-08,pdfbox,28e479410c55979fe627ca92546b00a2cad9d187,pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/PDType1CFont.java,dceea1d4ba5819ef93ef4a09b990b899fe7bd43a,pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/PDType1CFont.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,2,3.618230547143233e-08,myfaces-tobago,32c767eec209221d513c1c93158cbe8b293ae0ca,core/src/main/java/org/apache/myfaces/tobago/renderkit/html/HtmlRendererUtil.java,a41373968a5582b359412c923c1f58a085bfe2fe,core/src/main/java/org/apache/myfaces/tobago/renderkit/html/HtmlRendererUtil.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,3,0.0006618983333383432,hbase,6b21f8881be7649dadbdecd28dc2e2abe5c4ebe5,hbase-server/src/main/java/org/apache/hadoop/hbase/master/procedure/ServerCrashProcedure.java,010012cbcb3064b78b9e184a2808bbd26ea80903,hbase-server/src/main/java/org/apache/hadoop/hbase/master/procedure/ServerCrashProcedure.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,4,0.0006618983333383432,marmotta,8f612b0e438419c031f04b3fbf057e89825893ee,libraries/kiwi/kiwi-triplestore/src/main/java/org/apache/marmotta/kiwi/config/KiWiConfiguration.java,4b8b86802a9814f8cffbb03a5262b00f7a42f9a9,libraries/kiwi/kiwi-triplestore/src/main/java/org/apache/marmotta/kiwi/config/KiWiConfiguration.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,5,0.0006618983333383432,hbase,4cb40e6d846ce1f28ffb40d388c9efb753197813,hbase-server/src/main/java/org/apache/hadoop/hbase/LocalHBaseCluster.java,5d1b2110d1bac600d81d8bf04d3a97e5a9bd1268,hbase-server/src/main/java/org/apache/hadoop/hbase/LocalHBaseCluster.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,6,0.0006618983333383432,hive,9d96385ad96481f0ed206b3f8e24e4539c1cfdb5,metastore/src/java/org/apache/hadoop/hive/metastore/ObjectStore.java,6bd0c30b95106cac1d99a24e8fe2833c3ccc4379,metastore/src/java/org/apache/hadoop/hive/metastore/ObjectStore.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,7,0.0006618983333383432,ignite,4153b74e3c77ef8bbdd5ed99bab4db615390cab5,modules/core/src/main/java/org/apache/ignite/internal/processors/cache/GridCacheMapEntry.java,1b3742f4d7bedf0bb5c262786d386647b5b86e35,modules/core/java/org/gridgain/grid/kernal/processors/cache/GridCacheMapEntry.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,8,0.0006618983333383432,jackrabbit,812930fe637e4c84d7f31144ad3e1b150e2b708e,jackrabbit-core/src/main/java/org/apache/jackrabbit/core/xml/BufferedStringValue.java,c389acd08c94563a20c4e2aa5e065acfe568c65e,jackrabbit-core/src/main/java/org/apache/jackrabbit/core/xml/BufferedStringValue.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,9,0.0006618983333383432,phoenix,64658fe5a64e7089f5208ece25769bf644f96846,phoenix-core/src/main/java/org/apache/phoenix/query/QueryServicesOptions.java,28aebd6af3b635c98c8f1782295ea6c85167d659,phoenix-core/src/main/java/org/apache/phoenix/query/QueryServicesOptions.java,-
    # 5761172072fcfb0552c0657108df7f469faa8481,app/src/main/java/gradleTest/Increment/App.java,hansWork,-,10,0.0006618983333383432,chemistry-opencmis,967f40f298938d52f7d6fb1e02d9391247666561,chemistry-opencmis-server/chemistry-opencmis-server-bindings/src/main/java/org/apache/chemistry/opencmis/server/impl/webservices/ObjectService10.java,a1b384ebf93751c0f8db46c8be002bd5176b16cd,chemistry-opencmis-server/chemistry-opencmis-server-bindings/src/main/java/org/apache/chemistry/opencmis/server/impl/webservices/ObjectService10.java,-

    print_status("||| Step 2. Manually generating simfin result...")
    start = dt.datetime.now()

    bfic = pd.read_csv(f'/target/{hash_id}/commit_collector/BFIC.csv', names = ['Project', 'D4J ID', 'Faulty file path', 'Faulty line', 'FIC_sha', 'BFIC_sha']).values[1]
    buggy_commit_id = None
    clean_commit_id = None
    
    active_bugs = pd.read_csv(f'/home/codemodel/paths/defects4j/framework/projects/{identifier}/active-bugs.csv', names=["ID","buggy","clean","num","link"]).values
    for i in range(len(active_bugs)):
        if int(active_bugs[i][0]) == int(bug_id): # if the ID is same
            buggy_commit_id = active_bugs[i][1]
            clean_commit_id = active_bugs[i][2]
            break

    with open(f'./target/{hash_id}/simfin/test_result.csv', 'w', newline = '') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter = ',')

        csv_writer.writerow(['Y_BIC_SHA', 'Y_BIC_Path', 'Y_Project', 'Y_BugId', 'Rank', 'Sim-Score', 'Y^_Project', 'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk'])
        csv_writer.writerow([buggy_commit_id])



    end = dt.datetime.now()
    print_info(f"||| Time taken : {(end - start)}")



    executing_command = f"python3 ./pool/runner_web/prepare_pool_source_web.py -h {case['hash_id']}"
    print_status("||| Step 3. Preparing pool source for ConFix...")
    start = dt.datetime.now()
    exit_code = os.system(executing_command)
    end = dt.datetime.now()
    print_info(f"||| Time taken : {(end - start)}")

    if exit_code != 0:
        break

    print()

    executing_command = f"python3 ./confix/run_confix_web.py -d true -h {case['hash_id']}"
    print_status("||| Step 4. Executing ConFix...")
    start = dt.datetime.now()
    exit_code = os.system(executing_command)
    end = dt.datetime.now()
    print_info(f"||| Time taken : {(end - start)}")



    print("Processed arguments are as follows:")
    for argument in options_list:
        print(f"- {argument} : {case[argument]}")

        if case.get(argument, None) == None:
            input_validity = False
            print_error(f"{argument} is not valid.")

    if input_validity == False:
        print_error("Error : All arguments should given except case_file.")
        print("You can also give file as an argument to 'case_file' option, but the file should contain all arguments required in order:")
        for each in case.keys():
            if each == 'project_name':
                pass
            print(f"- {each.replace('_', '-')}")
        print()
        print("Terminating program.")
        sys.exit(-1)
    
    print()
    print("Do you wish to continue with those arguments? (Y/n) : ", end = '')
    answer = input()

    if answer not in ['', 'Y', 'y']:
        exit(1)

    if not is_test:
        case['hash_id'] = abs(hash(f"{case['project_name']}-{dt.datetime.now().strftime('%Y%m%d%H%M%S')}"))
    print_info(f"Hash ID generated as {case['hash_id']}. Find byproducts in ./target/{case['hash_id']}")

    # Run SimonFix Engine from those orders
    
    print()
    print("=" * 80)
    exit_code = 0
    executing_command = ""

    whole_start = dt.datetime.now()

    for i in range(1): # no loop, used to activate 'break'
        print()

        # Commit Collector
        

        # Prepare Pool Source

        executing_command = f"python3 ./pool/runner_web/prepare_pool_source_web.py -h {case['hash_id']}"
        print_status("||| Step 4. Preparing pool source for ConFix...")
        start = dt.datetime.now()
        exit_code = os.system(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

        if exit_code != 0:
            break

        print()

        # ConFix

        if case['is_defects4j'] == True:
            executing_command = f"python3 ./confix/run_confix_web.py -d true -h {case['hash_id']}"
        else:
            executing_command = f"python3 ./confix/run_confix_web.py -h {case['hash_id']} -i {case['source_path']},{case['target_path']},{case['test_list']},{case['test_target_path']},{case['compile_target_path']},{case['build_tool']}"
        print_status("||| Step 5. Executing ConFix...")
        start = dt.datetime.now()
        exit_code = os.system(executing_command)
        end = dt.datetime.now()
        print_info(f"||| Time taken : {(end - start)}")

    whole_end = dt.datetime.now()
    elapsed_time = (whole_end - whole_start)

    print()
    if exit_code != 0:
        print_error("||| SimonFix failed to find out the patch, or aborted due to abnormal exit.")
        print_info(f"||| Elapsed Time : {elapsed_time}")
    else:
        print_complete("||| SimonFix succeeded to find out the plausible patch.")
        print_info(f"||| Elapsed Time : {elapsed_time}")
        print_info("||| Generated Patch Content:")
        print()
        exit_code = os.system(f"cat ./target/{case['hash_id']}/diff_file.txt")
    print()
    print("=" * 80)

    


if __name__ == '__main__':
    main(sys.argv)
