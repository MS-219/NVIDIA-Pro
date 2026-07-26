# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY docker/maven-settings.xml /root/.m2/settings.xml
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -s /root/.m2/settings.xml -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -s /root/.m2/settings.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/uploads /device_agent

COPY --from=build /workspace/target/*.jar /app/app.jar
COPY --from=build /workspace/target/classes/ip2region.xdb /app/ip2region.xdb

ENV TZ=Asia/Shanghai
ENV IP2REGION_XDB_PATH=/app/ip2region.xdb

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8090/api/settings/banners >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/app.jar"]
