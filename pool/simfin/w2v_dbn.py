import csv
from gensim.models import Word2Vec
import logging
import numpy as np
import pandas as pd
import pickle
import platform
# from sklearn.model_selection import train_test_split
from sklearn.neighbors import KNeighborsClassifier

# dividing scenario of server and mac
HIDDEN_LAYER = []
W2V_DIM = 0
EPOCH_RBM = 0
BATCH_SIZE = 0
if platform.system() == 'Linux':
    from dbn.tensorflow import UnsupervisedDBN

    HIDDEN_LAYER = [100] * 10
    W2V_DIM = 10
    EPOCH_RBM = 5
    BATCH_SIZE = 32
else:
    from dbn import UnsupervisedDBN

    HIDDEN_LAYER = [32, 16]
    W2V_DIM = 1
    EPOCH_RBM = 3
    BATCH_SIZE = 512

logging.basicConfig(
    format='%(asctime)s : %(levelname)s : %(message)s',
    level=logging.INFO
)


class W2VModel:
    def __init__(self, commits, file_path):
        self.commits = commits
        self.file_path = file_path
        self.model = None

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
        self.vectorized_commits = None

    def vectorize_commits(self, wv):
        """
        Vectorizes the commit corpus w.r.t. w2v model

        Args:
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


def write_test_result(out_file, testX, classifier):
    with open(out_file, 'w+') as file:
        for yhat in classifier.predict(testX):
            file.write(yhat + '\n\n')
    print('writing test on', out_file, 'complete!')
    return


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
        if line.startswith('<start>'):
            is_start = True
        if is_start:
            a_commit += line
        if line.startswith('<end>'):
            commit_list.append(a_commit.split())
            if len(a_commit.split()) > longest_len:
                longest_len = len(a_commit.split())
            a_commit = ""
            commit_count += 1
            is_start = False

    return commit_list, commit_count, longest_len


def vecs_on_csv(filePath, dbnX):
    # writing out the features learned by dbn on a csv file
    df = pd.DataFrame(data=dbnX[0:][0:],
                      index=[i for i in range(dbnX.shape[0])],
                      columns=['f' + str(i) for i in range(dbnX.shape[1])])
    df.to_csv(filePath)
    return


if __name__ == '__main__':
    ##########################################################################
    # DATA PREPARATION
    # read BIC corpus from txt file
    train_file = './inputs/100_code.txt'
    test_file = './inputs/math_code.txt'
    combined = './inputs/100_math_code.txt'

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

    ##########################################################################
    # Model Preparation

    X = np.asarray(train_cv)
    Y = pd.read_csv(train_label)

    # splitting test from one set
    # X_train, X_test, Y_train, Y_test = train_test_split(X, Y,
    #                                                     test_size=0.0004,
    #                                                     random_state=0)

    X_train = X
    Y_train = Y
    X_test = np.asarray(test_cv)
    Y_test = pd.read_csv(test_label)

    with open('./view_file/X_train.csv', '+w') as f:
        wr = csv.writer(f, dialect='excel')
        wr.writerows(X_train)

        wr.writerows(X_train)

    with open('./view_file/X_test.csv', '+w') as f:
        wr = csv.writer(f, dialect='excel')
        wr.writerows(X_test)

    print('original train data X (vectorized): ', X_train.shape)
    print('original test data X (vectorized): ', X_test.shape)

    knn = KNeighborsClassifier(n_neighbors=1)
    unsupervised_dbn = UnsupervisedDBN(hidden_layers_structure=HIDDEN_LAYER,
                                       batch_size=BATCH_SIZE,
                                       learning_rate_rbm=0.06,
                                       n_epochs_rbm=EPOCH_RBM,
                                       activation_function='sigmoid',
                                       verbose=True)

    X_dbn = unsupervised_dbn.fit_transform(X_train)
    X_dbn_test = unsupervised_dbn.fit_transform(X_test)

    print('X_train after dbn:', X_dbn.shape)
    print('Y_train:', Y_train.shape)

    vecs_on_csv('./view_file/X_dbn.csv', X_dbn)

    ##########################################################################
    # Model Training
    dbn_n_knn = knn.fit(X_dbn, Y_train.values.ravel())

    knn_classifier = KNeighborsClassifier(n_neighbors=1)
    knn_classifier.fit(X_train, Y_train.values.ravel())

    ##########################################################################
    # Model Evaluation
    print('X_test after dbn:', X_dbn_test.shape)
    print('Y_test: ', Y_test.shape)

    write_test_result('./eval/dbn_eval.txt', X_dbn_test, dbn_n_knn)

    print('program finished!')

# @misc{DBNAlbert,
#     title={A Python implementation of Deep Belief Networks built upon
#     NumPy and TensorFlow with scikit-learn compatibility},
#     url={https://github.com/albertbup/deep-belief-network},
#     author={albertbup},
#     year={2017}}
