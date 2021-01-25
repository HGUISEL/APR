import csv
import logging
import numpy as np
import pandas as pd
from gensim.models import Word2Vec
from keras.layers import Input, LSTM, Dense, RepeatVector, CuDNNLSTM
from keras.models import Model
from keras.preprocessing.sequence import pad_sequences
import pickle
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import MinMaxScaler

np.set_printoptions(threshold=np.inf)

logging.basicConfig(
    format='%(asctime)s : %(levelname)s : %(message)s',
    level=logging.INFO
)

W2V_DIM = 100


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


def read_commits(input_file):
    """
    Parses each commits from corpus and returns info.

    Args:   input_file      (string):          file name of commit corpus

    Returns:
            commit_list     (list of string):  commits(list of string),
            commit_count    (int):             total number of counts,
            longest_len     (int):             length of longest commit
    """

    commit_corpus_file = open(input_file, 'r')
    commit_list = []
    is_start = False
    a_commit = ""
    commit_count = 0
    longest_len = 0

    for line in commit_corpus_file:
        if line.startswith('<SOC>'):
            is_start = True
        if is_start:
            a_commit += line
        if line.startswith('<EOC>'):
            commit_list.append(a_commit.split())
            if len(a_commit.split()) > longest_len:
                longest_len = len(a_commit.split())
            a_commit = ""
            commit_count += 1
            is_start = False

    return commit_list, commit_count, longest_len

class W2VModel:
    def __init__(self, commits, file_path):
        self.commits = commits
        self.file_path = file_path

    def saveW2V(self):
        model = Word2Vec(self.commits,
                         size=W2V_DIM,
                         window=10,
                         min_count=1,
                         workers=32,
                         sg=1)
        model.save(self.file_path)
        self.model = model
        print('saving', self.file_path, 'complete!')
        return

    def loadW2V(self):
        self.model = Word2Vec.load(self.file_path)
        print('loading', self.file_path, 'complete!')
        return

    def trainFromLoad(self, newList):
        self.model.train(newList, total_examples=10, epochs=10, word_count=0)
        return


class CommitVectors:
    def __init__(self, commits, commit_count, longest_len):
        self.commits = commits
        self.commit_count = commit_count
        self.longest_len = longest_len

    def vectorize_commits(self, wv):
        """
        Vectorizes the commit corpus w.r.t. w2v model

        Args:
                commit_list     (list of string):  commits(list of string),
                commit_count    (int):             total number of counts,
                longest_len     (int):             length of longest commit
                wv              (w2v model):       w2v trained from corpus

        Returns vectorized_commits ([commit_count][longest_len][1])

        """
        commits = np.asarray(self.commits)
        vectorized_commits = []
        for commit in commits:
            vector_of_commit = []
            for token in commit:
                vector_of_token = wv[token]
                for val in vector_of_token:
                    vector_of_commit.append(val)
            if len(commit) < self.longest_len:
                for i in range(self.longest_len - len(commit)):
                    for j in range(W2V_DIM):
                        vector_of_commit.append(0.0)
            vectorized_commits.append(vector_of_commit)

        self.vectorized_commits = vectorized_commits
        print('vectorizing commits complete!')
        return

    def write_cv(self, filePath):
        file = open(filePath, 'wb')
        pickle.dump(self.vectorized_commits, file)
        file.close()
        print('writing commit vectors complete!')
        return

    def read_cv(self, filePath):
        file = open(filePath, 'rb')
        cvs = pickle.load(file)
        file.close()
        self.vectorized_commits = cvs
        print('reading commit vectors complete!')
        return cvs


if __name__ == '__main__':
    ##########################################################################
    # DATA PREPARATION
    # read BIC corpus from txt file
    train_file = './inputs/string/S_train.txt'
    test_file = './inputs/string/S_calcite.txt'
    combined = './inputs/string/S_combined.txt'

    # parses the commit corpus
    train_commits, train_com_cnt, train_lgst_len = read_commits(train_file)
    test_commits, test_com_cnt, test_lgst_len = read_commits(test_file)
    combined_commits, combined_com_cnt, combined_lgst_len = read_commits(
        combined)

    # train/load W2VModel
    w2v = W2VModel(combined_commits, './word2vec/combined_w2v.model')
    w2v.saveW2V()
    # w2v.loadW2V()

    # vectorizing the corpus with w2v model
    train_cvs = CommitVectors(train_commits, train_com_cnt, combined_lgst_len)
    train_cvs.vectorize_commits(w2v.model.wv)
    train_cvs.write_cv('./commit_vec/train_cvs.pkl')
    # train_cvs.read_cv('./commit_vec/train_cvs.pkl')

    test_cvs = CommitVectors(test_commits, test_com_cnt, combined_lgst_len)
    test_cvs.vectorize_commits(w2v.model.wv)
    test_cvs.write_cv('./commit_vec/test_cvs.pkl')
    # test_cvs.read_cv('./commit_vec/test_cvs.pkl')

    # data before
    train_cv = train_cvs.vectorized_commits
    train_label = './inputs/100_label.csv'
    test_cv = test_cvs.vectorized_commits
    test_label = './inputs/math_label.csv'

    X_train = np.asarray(train_cv)
    Y_train = pd.read_csv(train_label)
    X_test = np.asarray(test_cv)
    Y_test = pd.read_csv(test_label)

    scaler = MinMaxScaler()
    scaler.fit(X_train)

    X_train = scaler.transform(X_train)
    X_test = scaler.transform(X_test)

    input_num = X_train.shape[0]
    time_step = X_train.shape[1]
    input_dim = W2V_DIM
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
