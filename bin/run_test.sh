jarfile=`ls -t target/lupine*.jar | head -n1`
java -Dlog4j.configuration=file:conf/log4j.xml -cp $jarfile  org.caseyandgary.JMPlayer -config conf/config.xml
