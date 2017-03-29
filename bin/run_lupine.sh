jarfile=`ls -t target/lupine*.jar | head -n1`
java -jar -Dlog4j.configuration=file:conf/log4j.xml $jarfile -config conf/config.xml
