JAVA_HOME='/usr/java/jdk1.8.0_121'
jarfile=`ls -t target/*.jar | head -n1`
$JAVA_HOME/bin/java -jar -Dlog4j.configuration=file:conf/log4j.xml $jarfile -config conf/config.xml
