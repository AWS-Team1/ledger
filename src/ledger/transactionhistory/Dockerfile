FROM maven:3.9-eclipse-temurin-17@sha256:1e539ad894cb86c5b87a9c81223732a90c6cb7c23e4ac206c2cea892538fd8d7 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre@sha256:bd25c61779663bc2df8ced059f1cc114fafc073afe16125c13fcc3a37fecd845
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar

ARG SECURITY_REFRESH=manual

RUN set -eux; \
    echo "security refresh: ${SECURITY_REFRESH}"; \
    rm -f /usr/bin/pebble; \
    apt-get update; \
    apt-get upgrade -y; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/*

USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
