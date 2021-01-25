cd ./ChangeVectorCollector
rm -rf ./build/distributions/change-vector-collector
gradle distzip
cd ./build/distributions/
unzip change-vector-collector.zip
