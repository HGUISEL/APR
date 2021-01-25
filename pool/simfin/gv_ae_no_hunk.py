import csv
import getopt
from keras.layers import Input, Dense
from keras.models import Model
from keras.models import load_model
from keras.preprocessing.sequence import pad_sequences
import logging
import numpy as np
import pandas as pd
import pickle
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import MinMaxScaler
import sys
K_NEIGHBORS = 1

np.set_printoptions(threshold=np.inf)

logging.basicConfig(
    format='%(asctime)s : %(levelname)s : %(message)s',
    level=logging.INFO
)


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


def loadGumVec(train_file, train_label, test_file, test_label):
    f_trainX = open(train_file, 'r')
    trainX = csv.reader(f_trainX)
    f_testX = open(test_file, 'r')
    testX = csv.reader(f_testX)

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
    testX = np.asarray(list(testX))
    testY = pd.read_csv(test_label, names=['index',
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

    return new_trainX, trainY.values, new_testX, testY.values


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


def run_train(X_train, Y_train, train):
    ##########################################################################
    # DATA PREPARATION

    print('original X_train.shape: ', X_train.shape)
    print('original Y_train.shape: ', Y_train.shape)

    Y_train_label = Y_train[:, 8]

    ##########################################################################
    # Model Preparation

    # Applying minmax scaler to instances
    scaler = MinMaxScaler()
    scaler.fit(X_train)

    write_pickle(scaler, './PatchSuggestion/models/' + train + '_scaler.pkl')
    X_train = scaler.transform(X_train)

    print('\noriginal train data X (vectorized): ', X_train.shape)

    # Preparing Deep AE
    feature_dim = X_train.shape[1]
    input_commit = Input(shape=(feature_dim,))
    encoded = Dense(500, activation='relu')(input_commit)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu')(encoded)
    encoded = Dense(500, activation='relu', name='encoder')(encoded)

    decoded = Dense(500, activation='relu')(encoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(500, activation='relu')(decoded)
    decoded = Dense(500, activation='relu')(decoded)
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

    # encoding dataset
    X_train_encoded = T_encoder.predict(X_train)

    T_encoder.save('./PatchSuggestion/models/' + train +
                   '_encoder.model', include_optimizer=True)

    # wrting encoded dataset for checking
    vecs_on_csv('./PatchSuggestion/view_file/' + train + '_encoded.csv',
                X_train_encoded)

    print('\nX_encoded:', X_train_encoded.shape)

    # training encoder + knn classifier
    knn = KNeighborsClassifier(n_neighbors=K_NEIGHBORS,
                               metric='manhattan',
                               algorithm='auto',
                               weights='distance')

    knn.fit(X_train_encoded.astype(str), Y_train_label)

    write_pickle(knn, './PatchSuggestion/models/' + train + '_knn.model')

    return


def run_predict(X_test, Y_test, Y_train, test, train):
    ##########################################################################
    # Model Evaluation

    # loading models
    encoder = load_model('./PatchSuggestion/models/' +
                         train + '_encoder.model', compile=False)
    knn = load_pickle('./PatchSuggestion/models/' + train + '_knn.model')
    scaler = load_pickle('./PatchSuggestion/models/' + train + '_scaler.pkl')

    X_test = scaler.transform(X_test)

    # encoding test set through learned encoder
    X_test_encoded = encoder.predict(X_test)

    # wrting encoded testset for checking
    vecs_on_csv('./PatchSuggestion/view_file/' + test + '_encoded.csv', X_test_encoded)

    # writing the result of knn prediction
    write_kneighbors('./output/eval/' + test +
                     '_gv_ae_kneighbors.txt', X_test_encoded, knn)
    write_test_result('./output/eval/' + test +
                      '_gv_ae_predict.txt', X_test_encoded, knn)
    resultFile = './output/eval/' + test + '_result.csv'
    write_result(Y_train,
                 Y_test,
                 resultFile,
                 X_test_encoded,
                 knn)
    print('run_predict complete!')


def main(argv):
    global K_NEIGHBORS
    train_name = 'train'
    test_name = 'test'

    try:
        opts, args = getopt.getopt(argv[1:], "ht:k:p:", ["help", "train", "k_neighbors", "predict"])
    except getopt.GetoptError as err:
        print(err)
        sys.exit(2)
    is_predict = False
    is_train = False
    for o, a in opts:
        if o in ("-h", "--help"):
            print("")
            sys.exit()
        elif o in ("-t", "--train"):
            train_name = a
            is_train = True
        elif o in ("-k", "--k_neighbors"):
            K_NEIGHBORS = int(a)
        elif o in ("-p", "--predict"):
            is_predict = True
            test_name = a
        else:
            assert False, "unhandled option"

    # load Gumtree Vectors
    trainX, trainY, testX, testY = loadGumVec(
        './output/trainset/GVNC_' + train_name + '.csv',
        './output/trainset/Y_' + train_name + '.csv',
        './output/testset/GVNC_' + test_name + '.csv',
        './output/testset/Y_' + test_name + '.csv'
    )

    print('after load trainX.shape: ', trainX.shape)
    print('after load testX.shape: ', testX.shape)

    if is_train:
        run_train(trainX, trainY, train_name)
    if is_predict:
        run_predict(testX, testY, trainY, test_name, train_name)


if __name__ == '__main__':
    main(sys.argv)
