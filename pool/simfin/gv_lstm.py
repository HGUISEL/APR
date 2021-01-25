import csv
import logging
import numpy as np
import pandas as pd

from keras.layers import Input, LSTM, Dense, RepeatVector, CuDNNLSTM
from keras.models import Model
from keras.preprocessing.sequence import pad_sequences
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import MinMaxScaler

np.set_printoptions(threshold=np.inf)

logging.basicConfig(
    format='%(asctime)s : %(levelname)s : %(message)s',
    level=logging.INFO
)


def write_kneighbors(out_file, testX, classifier):
    score = 0
    kneighbors = classifier.kneighbors(testX)
    with open(out_file, 'w') as fp:
        for i in range(len(kneighbors[0])):
            if np.any(kneighbors[0][i] < 0.001):
                score += 1
            fp.write(str(i) + ': ' + str(kneighbors[0][i]) + ' ' + str(kneighbors[1][i]) + '\n')
        # fp.write(str(kneighbors))
    print('score:', score)
    print('writing test on', out_file, 'complete!')
    return


def loadGumVec(train_file, train_label, test_file, test_label):
    f_trainX = open(train_file, 'r')
    trainX = csv.reader(f_trainX)
    f_testX = open(test_file, 'r')
    testX = csv.reader(f_testX)

    trainX = np.asarray(list(trainX))
    trainY = pd.read_csv(train_label)
    testX = np.asarray(list(testX))
    testY = pd.read_csv(test_label)

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
    # for i in range(len(trainX)):
    #     for j in range(len(trainX[i])):
    #         if trainX[i][j] == '':
    #             trainX[i][j] = 0
    #         else:
    #             trainX[i][j] = int(trainX[i][j])
    #     for j in range(train_max - len(trainX[i])):
    #         trainX[i].append(0)
    # for i in range(len(testX)):
    #     for j in range(len(testX[i])):
    #         if testX[i][j] == '':
    #             testX[i][j] = 0
    #         else:
    #             testX[i][j] = int(testX[i][j])
    #     for j in range(test_max - len(testX[i])):
    #         testX[i].append(0)

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
            new_testX[i] = np.concatenate([testX[i], np.zeros(shape=(train_max - test_max))])
    if test_max > train_max:
        new_trainX = np.zeros(shape=(len(trainX), test_max))
        new_testX = np.zeros(shape=(len(testX), test_max))
        for i in range(len(testX)):
            new_testX[i] = np.asarray(testX[i])
        for i in range(len(trainX)):
            new_trainX[i] = np.concatenate([trainX[i], np.zeros(shape=(test_max - train_max))])

    f_trainX.close()
    f_testX.close()

    return new_trainX, trainY.values, new_testX, testY.values


if __name__ == '__main__':
    ##########################################################################
    # DATA PREPARATION

    # load commit String
    X_train, Y_train, X_test, Y_test = loadGumVec(
        './inputs/apache/GVNC_train.csv',
        './inputs/apache/Y_train.csv',
        './inputs/apache/GVNC_calcite.csv',
        './inputs/apache/Y_calcite.csv'
    )

    scaler = MinMaxScaler()
    scaler.fit(X_train)

    X_train = scaler.transform(X_train)
    X_test = scaler.transform(X_test)

    input_num = X_train.shape[0]
    time_step = X_train.shape[1]
    input_dim = 1
    latent_dim = 100
    X_train = np.reshape(X_train, (input_num, time_step, input_dim))
    X_test = np.reshape(X_test, (X_test.shape[0], time_step, input_dim))

    print(X_train.shape)
    print(X_test.shape)
    print(input_num)
    print(time_step)

    inputs = Input(shape=(time_step, input_dim))
    encoded = LSTM(latent_dim, name='encoder')(inputs)

    decoded = RepeatVector(time_step)(encoded)
    decoded = LSTM(input_dim, return_sequences=True)(decoded)

    auto = Model(inputs, decoded)
    encoder = Model(inputs, encoded)

    auto.compile(optimizer='adadelta', loss='binary_crossentropy')
    auto.fit(X_train, X_train, batch_size=512, epochs=1, verbose=1)

    T_autoencoder = auto
    T_encoder = Model(inputs=T_autoencoder.input, outputs=T_autoencoder.get_layer('encoder').output)

    X_train_encoded = T_encoder.predict(X_train)
    X_test_encoded = T_encoder.predict(X_test)

    # training encoder + knn classifier
    knn = KNeighborsClassifier(n_neighbors=10, metric='manhattan', algorithm='auto', weights='distance')
    knn_n_encoder = knn.fit(X_train_encoded, Y_train)

    ##########################################################################
    # Model Evaluation

    write_kneighbors('eval/lstm_gumvec_kneighbors.txt', X_test_encoded, knn_n_encoder)

    print('program finished!')
