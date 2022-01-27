import os  # nope

def main():
    # IF AND ONLY IF POOL SOURCE IS READY
    
    input_string = "app/src/main,app/build/classes/java,gradleTest.Increment.AppTest,app/build/classes/java/main:app/build/classes/java/test:lib/junit-4.11.jar,app/build/classes/java/main,gradle"
    # change hash input if target directory changes
    hash_input = "testing2"

    os.system("cd ~/hans/APR")
    
    # remove previous candidates and patches for custom confix run
    os.system("rm -rf target/"+hash_input+"/gradleTest-Increment/candidates")
    os.system("rm -rf target/"+hash_input+"/gradleTest-Increment/patches")

    # run confix on testing2
    print("python3 ./confix/run_confix_web.py -h "+hash_input+" -i "+input_string)
    os.system("python3 ./confix/run_confix_web.py -h "+hash_input+" -i "+input_string)

if __name__ == '__main__':
    main()
