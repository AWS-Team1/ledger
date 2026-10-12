FROM maven:3.9-eclipse-temurin-17@sha256:1e539ad894cb86c5b87a9c81223732a90c6cb7c23e4ac206c2cea892538fd8d7 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre@sha256:207ecae0b2b104dfc6dfa763d1e9cd2041cb344800a180e24cb1a28ed533ff90
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
