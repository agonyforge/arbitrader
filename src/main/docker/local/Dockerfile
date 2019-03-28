FROM openjdk:8-jre-alpine
MAINTAINER Peter Keeler <peter@r307.com>
EXPOSE 8080
COPY arbitrader-*.jar /opt/app/app.jar
CMD ["/usr/bin/java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-jar", "/opt/app/app.jar"]
