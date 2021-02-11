FROM amazoncorretto:8-alpine
MAINTAINER Peter Keeler <peter@agonyforge.com>
LABEL org.opencontainers.image.source="https://github.com/agonyforge/arbitrader"
EXPOSE 8080
COPY arbitrader-*.jar /opt/app/app.jar
CMD ["/usr/bin/java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-jar", "/opt/app/app.jar"]
