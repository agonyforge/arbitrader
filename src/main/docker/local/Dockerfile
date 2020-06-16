FROM adoptopenjdk/openjdk11:alpine-jre
MAINTAINER Peter Keeler <peter@r307.com>
EXPOSE 8080
COPY arbitrader-*.jar /opt/app/app.jar
CMD ["/opt/java/openjdk/bin/java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "/opt/app/app.jar"]
