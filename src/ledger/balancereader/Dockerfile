FROM maven:3.9-eclipse-temurin-17@sha256:0af1de9f9b587b9f57a97379c292dc1c1d3ff61a476fdfa1b5eb42ff5b3cbc0f AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre@sha256:611e326900ba9edcedfb246ede24efc66a911fa496ac623db9f33cae88a2d83b
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
# Trivy: base 이미지의 미사용 Go 바이너리 pebble 제거(Go stdlib/x-net CVE) + OS 보안 업데이트
RUN rm -f /usr/bin/pebble \
    && apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
