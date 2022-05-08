cd /tmp/httpStub
export PATH=$PATH:/usr/bin/java
export CLASSPATH=/tmp/httpStub/httpStub.jar:/tmp/httpStub/lib/javax.json-1.0.jar:/tmp/httpStub/lib/commons-pool2-2.6.2.jar:/tmp/httpStub/lib/jedis-2.8.1.jar:/tmp/httpStub/lib/jsr166.jar

java -Xmx512m httpStub.httpStub