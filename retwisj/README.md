Spring Data - Key Value - Redis Twitter Example
===============================================

A Java implementation of the [Redis Twitter Clone] (http://redis.io/topics/twitter-clone) using Spring Data.

Build
-----
The project creates a WAR file suitable for deployment in a Servlet 2.5 container (such as Tomcat). It uses [Gradle](http://gradle.org/) as a build system.
Simply type:

gradlew build

or if you have gradle installed on your machine and in your classpath:

gradle build

Start up an instance of the redis server, deploy your WAR and point your browser to http://<server>:<port>/retwis