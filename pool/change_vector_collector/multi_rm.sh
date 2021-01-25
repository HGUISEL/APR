#!/bin/bash

filepath='./assets/rm_zero/train_clean/X_*'
for eachfile in $filepath
do
    project=${eachfile}
    url=$project
    basename=$(basename $url)
    subStr=$(echo $basename | cut -d'_' -f 2)
    subStr=$(echo $subStr | cut -d'.' -f 1)
    # Project name,ISSUE KEY,github
    fileName="BBIC_"$subStr".csv"
    ./change-vector-collector/bin/change-vector-collector -z -u https://github.com/apache/$subStr -i "./assets/rm_zero/train_clean/" -o "./assets/rm_zero/train_clean/out/"
    echo $subStr done
done
