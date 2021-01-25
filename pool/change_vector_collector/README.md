# ChangeVectorCollector

ChangeVectorCollector is a tool that uses GumTree to differentiate two code between a change.

### How it works:
1. Takes a csv file that holds target commits and the target changed line.
<br> You can use the results from [BugPatchCollector](https://github.com/HGUISEL/bugpatchcollector) to find difference of code when BIC occurs.
2. Blames the changed line to get the commit before it is changed.
3. Extracts the files that is before and after the change.
4. Compares the change of files using GumTree distribution from https://github.com/GumTreeDiff/gumtree
5. Difference of code is vectorized with the attribute counts of changes w.r.t AST node types.
6. The output file is in the form of arff format.
7. Different correlation coefficients can be computed to the change vectors.

<br>

# How to build: Gradle
<pre><code> $ ./gradlew distZip </code></pre>
or
<pre><code> $ gradle distZip </code></pre>

After the command do follwing to unzip the distZip:
<pre><code> unzip /build/distributions/change-vector-collector.zip </code></pre>

The executable file is in build/distributions/change-vector-collector/bin

If you have trouble to build using gradlew, enter
<pre><code>$ gradle wrap</code></pre>

 
 # Options
 >Required options 
* -u (url) The url of the target GitHub repository.
* -i (input) file path of the input file.
* -o (output) file path of the output file.
>Must choose one of the following options
* -r (repo) from target commit, collect before change from repo.
<br> -i is the file path with target commit's SHA and path in the repo and the target line number.
<br> -o is the file path and the file name you want to store the change-vector arff.
* -l (local) if you already have information of before changes. 
<br> -i is the file path with the information of before and after the change
<br> -o is the same as above.
* -c (correlation) computes correlations after retriveing the change vectors.
<br> -i is the file path of the change vector arff.
<br> -o is the folder path where you want to store the correlation csvs.

# Example Tests 

<pre><code> -r -u "https://github.com/apache/zookeeper" -i "./assets/BIC_zookeeper.csv" -o "./assets/CVC_zookeper.arff" </code></pre>

<pre><code> -l -u "https://github.com/apache/zookeeper" -i "./assets/BBIC_zookeeper.csv" -o "./assets/CVC_zookeper.arff" </code></pre>

<pre><code> -c -u "https://github.com/apache/zookeeper" -i "./assets/cvc.arff" -o "./assets/correlation/" </code></pre>

