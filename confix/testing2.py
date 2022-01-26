import os  # nope

def main():
    input_string = "app/src/main,app/build/classes/java,gradleTest.Increment.AppTest,app/build/classes/java/main:app/build/classes/java/test:lib/junit-4.11.jar,app/build/classes/java/main,gradle"
    hash_input = "testing2"
    

    os.system("cd ~/hans/APR")
    print("python3 ./confix/run_confix_web.py -h "+hash_input+" -i "+input_string)
    os.system("python3 ./confix/run_confix_web.py -h "+hash_input+" -i "+input_string)

if __name__ == '__main__':
    main()
