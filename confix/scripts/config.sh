#!/bin/sh
if [ "$#" -ne 4 ] ; then
  echo "Usage: $0 DIRECTORY" >&2
  exit 1
fi
CDIR=$(pwd)
DIR=../../results
PROP="confix.properties"
echo "Create confix.properties for $CDIR"
cp ../../../confix/properties/$PROP ./$PROP
#cp ../../properties/$PROP ./$PROP

#Export properties
value=$(defects4j export -p dir.src.classes)
echo "src.dir=$value" >> $PROP
value=$(defects4j export -p dir.bin.classes)
echo "target.dir=$value" >> $PROP
value=$(defects4j export -p dir.src.tests)
echo "test.dir=$value" >> $PROP
value=$(defects4j export -p cp.compile)
echo "cp.compile=$value" >> $PROP
value=$(defects4j export -p cp.test)
echo "cp.test=$value" >> $PROP

echo "projectName=${1}" >> $PROP
echo "bugId=${2}" >> $PROP
echo "pFaultyClass=${3}" >> $PROP
echo "pFaultyLine=${4}" >> $PROP

#Create test lists
defects4j export -p tests.all > tests.all
defects4j export -p tests.relevant > tests.relevant
defects4j export -p tests.trigger > tests.trigger