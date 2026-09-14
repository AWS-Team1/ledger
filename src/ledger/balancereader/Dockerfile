FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
# Trivy: base 이미지의 미사용 Go 바이너리 pebble 제거(Go stdlib/x-net CVE) + OS 보안 업데이트
RUN rm -f /usr/bin/pebble \
    && apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
