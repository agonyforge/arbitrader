FROM adoptopenjdk/openjdk11:alpine as build
MAINTAINER Peter Keeler <peter@r307.com>
WORKDIR /opt/build
COPY . /opt/build/
RUN cd /opt/build \
&& apk update \
&& apk add --no-cache bash \
&& ./gradlew --console=plain clean build -x buildDocker -x dependencyCheckAnalyze

FROM adoptopenjdk/openjdk11:alpine-jre
MAINTAINER Peter Keeler <peter@r307.com>
EXPOSE 8080
COPY --from=build /opt/build/build/libs/arbitrader-*.jar /opt/app/app.jar
CMD ["/opt/java/openjdk/bin/java", "-jar", "/opt/app/app.jar"]
