# user-service 镜像。构建上下文必须是仓库根目录。
# 这样可以同时读取 msa/lightmark-common，且不会把其他 service 作为依赖打进来。
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY msa/pom.xml msa/pom.xml
COPY msa/lightmark-common/pom.xml msa/lightmark-common/pom.xml
COPY msa/user-service/pom.xml msa/user-service/pom.xml
COPY msa/product-service/pom.xml msa/product-service/pom.xml
COPY msa/order-service/pom.xml msa/order-service/pom.xml
COPY msa/content-service/pom.xml msa/content-service/pom.xml

COPY msa/lightmark-common/src msa/lightmark-common/src
COPY msa/user-service/src msa/user-service/src

RUN mvn -B -ntp -f msa/pom.xml -pl user-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

ARG APP_VERSION=0.0.1-SNAPSHOT
ENV APP_VERSION=${APP_VERSION}
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

COPY --from=build /workspace/msa/user-service/target/user-service-*-boot.jar /app/app.jar

USER 10001
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
