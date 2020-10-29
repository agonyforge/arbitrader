FROM amazoncorretto:8-alpine
MAINTAINER Peter Keeler <peter@agonyengine.com>
LABEL org.opencontainers.image.source="https://github.com/scionaltera/arbitrader"
EXPOSE 8080
COPY arbitrader-*.jar /opt/app/app.jar
CMD ["/usr/bin/java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-jar", "/opt/app/app.jar"]
