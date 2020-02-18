#!/usr/bin/env bash
#
# ------------------------------------------------------------------------------
# This script performs fault-localization on a Java project using the GZoltar
# command line interface either using instrumentation 'at runtime' or 'offline'.
#
# Usage:
# ./run.sh
#     --instrumentation <online|offline>
#     [--help]
#
# Requirements:
# - `java` and `javac` needs to be set and must point to the Java installation.
#
# ------------------------------------------------------------------------------

SCRIPT_DIR=$(cd `dirname ${BASH_SOURCE[0]}` && pwd)

die() {
  echo "$@" >&2
  exit 1
}


# ------------------------------------------------------------------ Envs & Args

GZOLTAR_VERSION="1.7.3-SNAPSHOT"

# Check whether GZOLTAR_CLI_JAR is set
export GZOLTAR_CLI_JAR="$SCRIPT_DIR/lib/gzoltarcli.jar"
[ "$GZOLTAR_CLI_JAR" != "" ] || die "GZOLTAR_CLI is not set!"
#[ -s "$GZOLTAR_CLI_JAR" ] || die "$GZOLTAR_CLI_JAR does not exist or it is empty! Please go to '$SCRIPT_DIR/..' and run 'mvn clean install'."

# Check whether GZOLTAR_AGENT_RT_JAR is set
export GZOLTAR_AGENT_RT_JAR="$SCRIPT_DIR/lib/gzoltaragent.jar"
[ "$GZOLTAR_AGENT_RT_JAR" != "" ] || die "GZOLTAR_AGENT_RT_JAR is not set!"
[ -s "$GZOLTAR_AGENT_RT_JAR" ] || die "$GZOLTAR_AGENT_RT_JAR does not exist or it is empty! Please give valid lib directory contains com.gzoltar.agent.rt-VERSION-all.jar"

USAGE="Usage: ${BASH_SOURCE[0]} <Target project directory path> [--help]"
if [ "$#" -eq "0" ]; then
    die "$USAGE"
fi
if [ "$#" -ne "1" ]; then
    die "$USAGE"
fi



LIB_DIR="$SCRIPT_DIR/lib"
[ -d "$LIB_DIR" ] || die "$LIB_DIR does not exist!"
JUNIT_JAR="$LIB_DIR/junit.jar"
[ -s "$JUNIT_JAR" ] || die "$JUNIT_JAR does not exist or it is empty!"
HAMCREST_JAR="$LIB_DIR/hamcrest-core.jar"
[ -s "$HAMCREST_JAR" ] || die "$HAMCREST_JAR does not exist or it is empty!"
BUILD_DIR=$1/build
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" || die "Failed to create $BUILD_DIR!"

TARGET_DIR=$1/target
MVN_SRC_DIR=$TARGET_DIR/classes
MVN_TEST_DIR=$TARGET_DIR/test-classes
# ------------------------------------------------------------------------- Main

#
# Compile
#

echo "Compile source and test cases ..."

if [ -s "$1/pom.xml" ]; then
  cd $1
  mvn clean compile
  mvn clean test
#else
# find $SRC_DIR -wholename **/*.java > $BUILD_DIR/source_list.txt 
# find $TEST_DIR -wholename **/*Test.java > $BUILD_DIR/test_list.txt
#  javac "@$BUILD_DIR/source_list.txt" -d "$BUILD_DIR" || die "Failed to compile source code!"
#  echo "source compilation done!"
#  javac -cp $JUNIT_JAR:$SRC_DIR:$TEST_DIR:$BUILD_DIR "@$BUILD_DIR/test_list.txt" -d "$BUILD_DIR" || die "Failed to compile test cases!"
#  rm $BUILD_DIR/source_list.txt
#  rm $BUILD_DIR/test_list.txt
fi
echo "test case compilation done!"

#
# Collect list of unit test cases to run

echo "Collect list of unit test cases to run ..."

for file in $(find "$TARGET_DIR/surefire-reports" -name "*.txt")  
do  
  count=0;
  cat $file | while read line
  do
    count=$(($[count]+1))
    if [ $count -eq 2 ]; then
    CLASS_NAME_TEMP=`echo $line | cut -d':' -f2`

    
    elif [ $count -eq 4 ]; then
    temp=`echo $line | cut -d':' -f3`
    failure=`echo $temp | cut -d',' -f1`
    temp=`echo $line | cut -d':' -f4`
    error=`echo $temp | cut -d',' -f1`
    
      if [[ "$failure" != "0" || "$error" != "0" ]]; then
        CLASS_NAME=`echo $CLASS_NAME_TEMP | tr '.' '/'`
        ADD_NAME=".class"
        echo "${CLASS_NAME}${ADD_NAME}"  >> $BUILD_DIR/target_tests.txt
      fi 
    fi
  done
done 
UNIT_TESTS_FILE="$BUILD_DIR/tests.txt"
touch $UNIT_TESTS_FILE;
SUS_DIR="$BUILD_DIR/cand"
mkdir $SUS_DIR

cat $BUILD_DIR/target_tests.txt | while read line
do
  cp "$MVN_TEST_DIR/$line" "$SUS_DIR" 
done


java -cp $MVN_SRC_DIR:$MVN_TEST_DIR:$JUNIT_JAR:$HAMCREST_JAR:$GZOLTAR_CLI_JAR com.gzoltar.cli.Main \
    listTestMethods $SUS_DIR \
    --outputFile "$UNIT_TESTS_FILE" \
    #--includes ${Class_NAME}${*} || die "Collection of unit test cases has failed!"
[ -s "$UNIT_TESTS_FILE" ] || die "$UNIT_TESTS_FILE does not exist or it is empty!"

#
# Collect coverage
#
### 여기까지 잘되니까 건들지 말것. ###

SER_FILE="$BUILD_DIR/gzoltar.ser"

echo "Perform offline instrumentation ..."

  # Backup original classes
BUILD_BACKUP_DIR="$SCRIPT_DIR/build_backup"
rsync -a $TARGET_DIR/*classes $BUILD_BACKUP_DIR  || die "Backup of original classes has failed!"
rm -rf $TARGET_DIR

echo "The original classes are stored!"

  # Perform offline instrumentation
java -cp $BUILD_BACKUP_DIR/classes:$BUILD_BACKUP_DIR/test-classes:$GZOLTAR_AGENT_RT_JAR:$GZOLTAR_CLI_JAR \
com.gzoltar.cli.Main instrument \
--outputDirectory "$TARGET_DIR" \
$BUILD_BACKUP_DIR || die "Offline instrumentation has failed!"

echo "intrumentation done!"

echo "Run each unit test case in isolation ..."
  # Run each unit test case in isolation
java -cp $TARGET_DIR/classes:$TARGET_DIR/test-classes:$HAMCREST_JAR:$GZOLTAR_AGENT_RT_JAR:$GZOLTAR_CLI_JAR \
    -Dgzoltar-agent.destfile=$SER_FILE \
    -Dgzoltar-agent.output="file" \
    com.gzoltar.cli.Main runTestMethods \
    --testMethods "$UNIT_TESTS_FILE" \
    --offline \
    --collectCoverage || die "Coverage collection has failed!"


echo "Run unit test case is done!"

  # Restore original classes
cp -R $BUILD_BACKUP_DIR/* "$BUILD_DIR" || die "Restore of original classes has failed!"
rm -rf "$BUILD_BACKUP_DIR"

[ -s "$SER_FILE" ] || die "$SER_FILE does not exist or it is empty!"

echo "Back-up files are restored!"

#
# Create fault localization report
#

echo "Create fault localization report ..."

FL_DIR="$1/fl_results"
SPECTRA_FILE="$FL_DIR/sfl/txt/spectra.csv"
MATRIX_FILE="$FL_DIR/sfl/txt/matrix.txt"
TESTS_FILE="$FL_DIR/sfl/txt/tests.csv"



java -cp $BUILD_DIR/classes:$BUILD_DIR/test-classes:$JUNIT_JAR:$HAMCREST_JAR:$GZOLTAR_CLI_JAR \
  com.gzoltar.cli.Main faultLocalizationReport \
    --buildLocation "$BUILD_DIR" \
    --granularity "line" \
    --inclPublicMethods \
    --inclStaticConstructors \
    --inclDeprecatedMethods \
    --dataFile "$SER_FILE" \
    --outputDirectory "$FL_DIR" \
    --family "sfl" \
    --formula "ochiai" \
    --metric "entropy" \
    --formatter "txt" || die "Generation of fault-localization report has failed!"

[ -s "$SPECTRA_FILE" ] || die "$SPECTRA_FILE does not exist or it is empty!"
[ -s "$MATRIX_FILE" ] || die "$MATRIX_FILE does not exist or it is empty!"
[ -s "$TESTS_FILE" ] || die "$TESTS_FILE does not exist or it is empty!"

rsync -a $BUILD_DIR/test-classes/* $FL_DIR/test-classes/

rm -R $BUILD_DIR
rm -R $TARGET_DIR

echo "DONE!"
exit 0
