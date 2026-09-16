FROM maven:3-eclipse-temurin-26@sha256:4e980a3c7def35292c12ce1a1dc7872f02165f11717204c7b1c2dc477e40c1b8 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre@sha256:23d7f52652b6b65b57eab85df9631741807929a68ff7e37c27104de6ac6690d5
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
# Trivy: base 이미지의 미사용 Go 바이너리 pebble 제거(Go stdlib/x-net CVE) + OS 보안 업데이트
RUN rm -f /usr/bin/pebble \
    && apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
