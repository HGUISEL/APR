import os
import csv
import getopt
from keras.models import load_model
from keras.preprocessing.sequence import pad_sequences
import logging
import numpy as np
import pandas as pd
import scipy.spatial.distance as distance
from sklearn.preprocessing import MinMaxScaler
import sys

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

np.set_printoptions(threshold=np.inf)

logging.basicConfig(
    format='%(asctime)s : %(levelname)s : %(message)s',
    level=logging.INFO
)


def vecs_on_csv(filePath, X_dbn):
    # writing out the features learned by the model on a csv file
    df = pd.DataFrame(data=X_dbn[0:][0:],
                      index=[i for i in range(X_dbn.shape[0])],
                      columns=['f' + str(i) for i in range(X_dbn.shape[1])])
    df.to_csv(filePath)
    print('writing', filePath, 'complete!')
    return


def loadGumVec(train_file, test_file):
    f_trainX = open(train_file, 'r')
    trainX = csv.reader(f_trainX)
    f_testX = open(test_file, 'r')
    testX = csv.reader(f_testX)

    trainX = np.asarray(list(trainX))
    testX = np.asarray(list(testX))

    train_max = 0
    test_max = 0
    # get the max length of vecs
    for i in range(len(trainX)):
        if train_max < len(trainX[i]):
            train_max = len(trainX[i])
    for i in range(len(testX)):
        if test_max < len(testX[i]):
            test_max = len(testX[i])

    # apply zero padding for fix vector length
    for i in range(len(trainX)):
        for j in range(len(trainX[i])):
            if trainX[i][j] == '':
                trainX[i][j] = 0
            else:
                trainX[i][j] = int(trainX[i][j])
        for j in range(train_max - len(trainX[i])):
            trainX[i].append(0)
    for i in range(len(testX)):
        for j in range(len(testX[i])):
            if testX[i][j] == '':
                testX[i][j] = 0
            else:
                testX[i][j] = int(testX[i][j])
        for j in range(test_max - len(testX[i])):
            testX[i].append(0)

    trainX = pad_sequences(trainX, padding='post')
    testX = pad_sequences(testX, padding='post')

    new_trainX = None
    new_testX = None

    # unifying vec length of train and test
    if train_max >= test_max:
        new_trainX = np.zeros(shape=(len(trainX), train_max))
        for i in range(len(trainX)):
            new_trainX[i] = np.asarray(trainX[i])
        new_testX = np.zeros(shape=(len(testX), train_max))
        for i in range(len(testX)):
            new_testX[i] = np.concatenate(
                [testX[i], np.zeros(shape=(train_max - test_max))])
    if test_max > train_max:
        new_trainX = np.zeros(shape=(len(trainX), test_max))
        new_testX = np.zeros(shape=(len(testX), test_max))
        for i in range(len(testX)):
            new_testX[i] = np.asarray(testX[i])
        for i in range(len(trainX)):
            new_trainX[i] = np.concatenate(
                [trainX[i], np.zeros(shape=(test_max - train_max))])

    f_trainX.close()
    f_testX.close()
    return new_trainX, new_testX


def get_distance(file, X_train, X_test):
    # i = trainX, j = testX
    for i in range(len(X_test)):
        path = file + 'test' + str(i)
        # make folder for every test instance ex. /test0/
        if not os.path.isdir(path):
            os.mkdir(path)
        file_name = path + '/dist.csv'
        # open csv ex. /test0/BICSHA_BICPATH.csv
        with open(file_name, 'w', newline='') as csvfile:
            csv_writer = csv.writer(csvfile, delimiter=',')
            # writing distance between test_i and all trainset
            for j in range(len(X_train)):
                dist_i = distance.cityblock(X_test[i], X_train[j])
                csv_writer.writerow([dist_i])
        print('test', i, 'done!')

    return


def main(argv):
    train_name = 'no_input_for_train'
    test_name = 'no_input_for_test'

    try:
        opts, args = getopt.getopt(argv[1:], "ht:p:", ["help", "train", "predict"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)

    for o, a in opts:
        if o in ("-h", "--help"):
            print("")
            sys.exit()
        elif o in ("-t", "--train"):
            train_name = a
        elif o in ("-p", "--predict"):
            test_name = a
        else:
            assert False, "unhandled option"

    # 1. load vectors
    trainX, testX = loadGumVec(
        './output/trainset/X_' + train_name + '.csv',
        './output/testset/X_' + test_name + '.csv',
    )

    ##########################################################################
    # DATA PREPARATION

    print('original X_train.shape:', trainX.shape)

    ##########################################################################
    # Model Preparation

    # 2. apply scaler to both train & test set
    scaler = MinMaxScaler()
    scaler.fit(trainX)

    X_train = scaler.transform(trainX)
    X_test = scaler.transform(testX)

    ##########################################################################
    # Model Evaluation

    # 3. load AED model
    encoder = load_model('./PatchSuggestion/models/no_test_all3_encoder.model', compile=False)

    # 4. encode train & test set
    X_train_encoded = encoder.predict(X_train)
    X_test_encoded = encoder.predict(X_test)

    # 5. distance calculation
    resultFile = './data/jihoshin/' + test_name + '/'
    get_distance(resultFile, X_train_encoded, X_test_encoded)

    # writing the result of knn prediction

    print('loaded and predicted ' + test_name + '_' + train_name + '_result.csv complete!')


if __name__ == '__main__':
    main(sys.argv)
