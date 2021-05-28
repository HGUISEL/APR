cd /home/DPMiner/APR/confix/ConFix-code
mvn clean install
cp target/confix-0.0.1-SNAPSHOT-jar-with-dependencies.jar /home/DPMiner/APR/confix/lib/confix-ami.jar

cd /home/DPMiner/APR/confix/results/Math-59
/usr/bin/java -Xmx4g -cp /home/DPMiner/APR/confix/lib/las.jar:/home/DPMiner/APR/confix/lib/confix-ami.jar -Duser.language=en -Duser.timezone=America/Los_Angeles com.github.thwak.confix.main.ConFix