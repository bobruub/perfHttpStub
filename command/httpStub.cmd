@echo off

set PROGNAME=httpStub
set HOMELOC=C:\_git\perfHttpStub
rem set JAVALOC="C:\Program Files\Java\jre7\bin"

cd /d %HOMELOC%

set CLASSPATH=.;%HOMELOC%\httpStub.jar
set CLASSPATH=%CLASSPATH%;%HOMELOC%\lib\commons-pool2-2.6.2.jar
set CLASSPATH=%CLASSPATH%;%HOMELOC%\lib\javax.json-1.0.jar
set CLASSPATH=%CLASSPATH%;%HOMELOC%\lib\jedis-2.8.1.jar
set CLASSPATH=%CLASSPATH%;%HOMELOC%\lib\jsr166.jar

echo %CLASSPATH%

java -Xmx512m httpStub.httpStub 
