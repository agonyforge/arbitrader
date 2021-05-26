FROM amazoncorretto:8-alpine as build
MAINTAINER Peter Keeler <peter@agonyforge.com>
WORKDIR /opt/build
COPY . /opt/build/
RUN cd /opt/build \
&& apk update \
&& apk add --no-cache bash \
&& ./gradlew --console=plain clean build -x buildDocker -x dependencyCheckAnalyze

FROM amazoncorretto:8-alpine
MAINTAINER Peter Keeler <peter@agonyforge.com>
LABEL org.opencontainers.image.source="https://github.com/agonyforge/arbitrader"
EXPOSE 8080
COPY --from=build /opt/build/build/libs/arbitrader-*.jar /opt/app/app.jar
CMD ["/usr/bin/java", "-jar", "/opt/app/app.jar"]
