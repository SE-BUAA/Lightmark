# content-service 镜像：只构建 common 与当前服务，保持服务之间依赖隔离。
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY msa/pom.xml msa/pom.xml
COPY msa/lightmark-common/pom.xml msa/lightmark-common/pom.xml
COPY msa/content-service/pom.xml msa/content-service/pom.xml
COPY msa/lightmark-common/src msa/lightmark-common/src
COPY msa/content-service/src msa/content-service/src
RUN mvn -B -ntp -f msa/pom.xml -pl content-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG APP_VERSION=content-service-0.0.1-SNAPSHOT
ENV APP_VERSION=${APP_VERSION}
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
COPY --from=build /workspace/msa/content-service/target/content-service-*-boot.jar /app/app.jar
USER 10001
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
