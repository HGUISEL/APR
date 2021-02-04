PROJ_NAME_LIST=("Chart" "Closure" "Lang" "Math" "Time")
PROJ_LOWER_NAME_LIST=("chart" "closure" "lang" "math" "time")

PROJ_BUG_LIST=(26 133 65 106 27)

cd results

for i in {0..4} ; do
  j=1
  while [ $j -le ${PROJ_BUG_LIST[$i]} ]; do

    defects4j checkout -p ${PROJ_NAME_LIST[$i]} -v ${j}b -w ${PROJ_NAME_LIST[$i]}-${j}
    echo "\n=========================\n"
    echo Finish defects4j checkout
    echo "\n=========================\n"
    
    cp ../coverages/${PROJ_LOWER_NAME_LIST[$i]}/${PROJ_LOWER_NAME_LIST[$i]}${j}b/coverage-info.obj ${PROJ_NAME_LIST[$i]}-${j}



    cd ${PROJ_NAME_LIST[$i]}-${j}
    defects4j compile

    echo "\n========================\n"
    echo Finish defects4j compile
    echo "\n========================\n"

    ../../scripts/config.sh ${PROJ_NAME_LIST[$i]} ${j}
    echo "\n================\n"
    echo Finish config.sh
    echo "\n================\n"

    ../../scripts/confix.sh .
    echo "\n================\n"
    echo Finish confix.sh
    echo "\n================\n"
   
    
    echo "\n================\n"
    echo Copying Results...
    echo "\n================\n"
    mkdir ../patches/${PROJ_NAME_LIST[$i]}-${j}/
    cp -r ./patches/* ../patches/${PROJ_NAME_LIST[$i]}-${j}/
    echo "\n================\n"
    echo Done!
    echo "\n================\n"

    cd ../  
    # pwd: results
    
    
    mkdir patch-logs
    
    if [ -e ${PROJ_NAME_LIST[$i]}-${j}/patch_info ];then
        cp ${PROJ_NAME_LIST[$i]}-${j}/patch_info patch-logs/${PROJ_NAME_LIST[$i]}-${j}
    fi
    
    rm -rf ${PROJ_NAME_LIST[$i]}-${j}
    
    #echo $j
    j=`expr $j + 1`
  done
done
