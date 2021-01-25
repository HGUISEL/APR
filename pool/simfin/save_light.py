import sys
import os
import csv
import getopt
from keras.layers import Input, Dense
from keras.models import Model
from keras.preprocessing.sequence import pad_sequences
import numpy as np
import pandas as pd
import pickle
import random
from sklearn.preprocessing import MinMaxScaler
import tensorflow as tf
from tensorflow.python.keras import backend as K
os.environ['PYTHONHASHSEED'] = str(0)
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
np.set_printoptions(threshold=np.inf)
K_NEIGHBORS = 1


# fixing randomness
def set_random_seed(s):
    random.seed(0)
    np.random.seed(0)
    tf.random.set_seed(s)
    session_conf = tf.compat.v1.ConfigProto(intra_op_parallelism_threads=1, inter_op_parallelism_threads=1)
    sess = tf.compat.v1.Session(graph=tf.compat.v1.get_default_graph(), config=session_conf)
    K.set_session(sess)


def is_number(s):
    try:
        float(s)
        return True
    except ValueError:
        return False


def del_index_num(s):
    temp = ''
    is_passed = False
    for c in reversed(s):
        if is_number(c) and not is_passed:
            continue
        else:
            temp += c
            is_passed = True

    temp = list(temp)
    temp.reverse()
    s = ''.join(temp)

    return s


def write_kneighbors(out_file, testX, classifier):
    score = 0
    kneighbors = classifier.kneighbors(testX)
    with open(out_file, 'w') as fp:
        for i in range(len(kneighbors[0])):
            if np.any(kneighbors[0][i] < 0.001):
                score += 1
            fp.write(str(i) + ': ' +
                     str(kneighbors[0][i]) + ' ' + str(kneighbors[1][i]) + '\n')
        # fp.write(str(kneighbors))
    print('score:', score)
    print('writing test on', out_file, 'complete!')
    return


def write_test_result(out_file, testX, classifier):
    with open(out_file, 'w+') as file:
        for yhat in classifier.predict(testX):
            file.write(yhat + '\n\n')
    print('writing test on', out_file, 'complete!')
    return


def write_result(trainY, testY, out_file, testX, classifier):
    kneibors = classifier.kneighbors(testX)

    with open(out_file, 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile, delimiter=',')

        # writing header
        header = ['Y_BIC_SHA', 'Y_BIC_Path', 'Y_BIC_Hunk',
                  'Y_BFC_SHA', 'Y_BFC_Path', 'Y_BFC_Hunk',
                  'Rank', 'Sim-Score', 'BI_lines', 'Label',
                  'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BIC_Hunk',
                  'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk']

        csv_writer.writerow(header)

        # writing each row values (test * (predicted * k_keighbors))
        for i in range(len(testY)):
            # witing real answer (y)
            y_bic_sha = str(testY[i][3])
            y_bic_path = str(testY[i][1])
            y_bfc_sha = str(testY[i][7])
            y_bfc_path = str(testY[i][4])
            y_real_label = testY[i][10]

            y_bic_hunk = '-'
            y_bfc_hunk = '-'

            # writing predicted answers (y^)
            for j in range(K_NEIGHBORS):
                pred_idx = kneibors[1][i][j]
                yhat_bic_sha = str(trainY[pred_idx][3])
                yhat_bic_path = str(trainY[pred_idx][1])
                yhat_bfc_sha = str(trainY[pred_idx][7])
                yhat_bfc_path = str(trainY[pred_idx][4])

                yhat_bic_hunk = '-'
                yhat_bfc_hunk = '-'

                instance = [y_bic_sha, y_bic_path, y_bic_hunk,
                            y_bfc_sha, y_bfc_path, y_bfc_hunk,
                            j + 1, kneibors[0][i][j], '-', y_real_label,
                            yhat_bic_sha, yhat_bic_path, yhat_bic_hunk,
                            yhat_bfc_sha, yhat_bfc_path, yhat_bfc_hunk]

                csv_writer.writerow(instance)


def vecs_on_csv(filePath, X_dbn):
    # writing out the features learned by the model on a csv file
    df = pd.DataFrame(data=X_dbn[0:][0:],
                      index=[i for i in range(X_dbn.shape[0])],
                      columns=['f' + str(i) for i in range(X_dbn.shape[1])])
    df.to_csv(filePath)
    return


def loadGumVec(train_file, train_label):
    f_trainX = open(train_file, 'r')
    trainX = csv.reader(f_trainX)

    trainX = np.asarray(list(trainX))
    trainY = pd.read_csv(train_label, names=['index',
                                             'path_BBIC',
                                             'path_BIC',
                                             'sha_BBIC',
                                             'sha_BIC',
                                             'path_BBFC',
                                             'path_BFC',
                                             'sha_BBFC'
                                             'sha_BFC',
                                             'key',
                                             'project',
                                             'label'])

    train_max = 0
    # get the max length of vecs
    for i in range(len(trainX)):
        if train_max < len(trainX[i]):
            train_max = len(trainX[i])

    # apply zero padding for fix vector length
    for i in range(len(trainX)):
        for j in range(len(trainX[i])):
            if trainX[i][j] == '':
                trainX[i][j] = 0
            else:
                trainX[i][j] = int(trainX[i][j])
        for j in range(train_max - len(trainX[i])):
            trainX[i].append(0)

    trainX = pad_sequences(trainX, padding='post')

    f_trainX.close()

    return trainX, trainY.values


def write_pickle(src, filePath):
    file = open(filePath, 'wb')
    pickle.dump(src, file, protocol=4)
    file.close()
    print('writing on', filePath, 'complete!')
    return


def load_pickle(filePath):
    file = open(filePath, 'rb')
    data = pickle.load(file)
    file.close()
    return data


def main(argv):
    global K_NEIGHBORS
    train_name = 'no_input_for_train'
    seed = 0

    try:
        opts, args = getopt.getopt(argv[1:], "ht:s:", ["help", "train", "seed"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)

    for o, a in opts:
        if o in ("-h", "--help"):
            print("")
            sys.exit()
        elif o in ("-t", "--train"):
            train_name = a
        elif o in ("-s", "--seed"):
            seed = int(a)
        else:
            assert False, "unhandled option"

    # 0. fix seed for randomness
    set_random_seed(seed)
    
    # 1. load vectors
    trainX, trainY = loadGumVec(
        './output/trainset/X_' + train_name + '.csv',
        './output/trainset/Y_' + train_name + '.csv'
    )

    ##########################################################################
    # DATA PREPARATION

    print('original X_train.shape: ', trainX.shape)
    print('original Y_train.shape: ', trainY.shape)

    ##########################################################################
    # Model Preparation

    # 2. apply scaler to trainset
    scaler = MinMaxScaler()
    scaler.fit(trainX)

    X_train = scaler.transform(trainX)

    # 3. train AED model
    feature_dim = X_train.shape[1]
    input_commit = Input(shape=(feature_dim,))
    encoded = Dense(500, activation='relu')(input_commit)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu', name='encoder')(encoded)

    decoded = Dense(500, activation='relu')(encoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(feature_dim, activation='sigmoid')(decoded)

    ##########################################################################
    # Model Training

    # training autoencoder
    autoencoder = Model(input_commit, decoded)
    autoencoder.compile(loss='binary_crossentropy', optimizer='adadelta')

    autoencoder.fit(X_train, X_train, epochs=3, batch_size=256, shuffle=True)

    T_autoencoder = autoencoder
    T_encoder = Model(inputs=T_autoencoder.input,
                      outputs=T_autoencoder.get_layer('encoder').output)

    # 4. save AED model
    T_encoder.save('./PatchSuggestion/models/' + train_name + str(seed) + '_encoder.model', include_optimizer=True)

    print('saved ' + train_name + '_encoder.model complete!')


if __name__ == '__main__':
    main(sys.argv)
