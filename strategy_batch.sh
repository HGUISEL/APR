#!/bin/bash

for patchStrategy in "flfreq" "tested-first" "noctx" "patch"; do
    for concretizationStrategy in "tcvfl" "hash-match" "neighbor" "tc"; do

        # cp confix/properties/confix.properties "confix/properties/confix-$patchStrategy-$concretizationStrategy.properties"
        # echo "patch.strategy=$patchStrategy" >> "confix/properties/confix-$patchStrategy-$concretizationStrategy.properties"
        # echo "concretize.strategy=$concretizationStrategy" >> "confix/properties/confix-$patchStrategy-$concretizationStrategy.properties"

        sed -i "s/.*patch\.strategy=.*/patch\.strategy=$patchStrategy/g" confix/properties/confix.properties
        sed -i "s/.*concretize\.strategy=.*/concretize\.strategy=$concretizationStrategy/g" confix/properties/confix.properties

        now=$(date +"%c")
        echo "ConFix with patch strategy \"$patchStrategy\" and concretizaton strategy \"$concretizationStrategy\" run at time $now" >> different_strategy_batch.txt
        python3 fix-target-integrated.py test.txt

	    rm -rf target/batch_*

        # cat confix/properties/confix.properties
        # printf "\n"


    done
done
