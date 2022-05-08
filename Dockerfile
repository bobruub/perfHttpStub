# syntax=docker/dockerfile:1
FROM openjdk:16

RUN mkdir /tmp/httpStub
RUN mkdir /tmp/httpStub/config
RUN mkdir /tmp/httpStub/lib

WORKDIR /tmp/httpStub

ADD ./perfHttpStub.jar perfHttpStub.jar
ADD ./lib/jedis-2.8.1.jar /tmp/httpStub/lib/jedis-2.8.1.jar
ADD ./lib/javax.json-1.0.jar /tmp/httpStub/lib/javax.json-1.0.jar
ADD ./lib/commons-pool2-2.6.2.jar /tmp/httpStub/lib/commons-pool2-2.6.2.jar
ADD ./lib/json-smart-2.4.8.jar /tmp/httpStub/lib/json-smart-2.4.8.jar
ADD ./lib/asm-9.1.jar /tmp/httpStub/lib/asm-9.1.jar
ADD ./command/httpStub.sh httpStub.sh

EXPOSE 2525
COPY ./config/* /tmp/httpStub/config/
RUN cd /tmp/httpStub
ENV CLASSPATH=/tmp/httpStub/perfHttpStub.jar:/tmp/httpStub/lib/jedis-2.8.1.jar:/tmp/httpStub/lib/javax.json-1.0.jar:/tmp/httpStub/lib/commons-pool2-2.6.2.jar:/tmp/httpStub/lib/json-smart-2.4.8.jar:/tmp/httpStub/lib/asm-9.1.jar
CMD java -cp /tmp/httpStub/perfHttpStub.jar:/tmp/httpStub/lib/jedis-2.8.1.jar:/tmp/httpStub/lib/javax.json-1.0.jar:/tmp/httpStub/lib/commons-pool2-2.6.2.jar:/tmp/httpStub/lib/json-smart-2.4.8.jar:/tmp/httpStub/lib/asm-9.1.jar -Xmx512m httpStub.httpStub
