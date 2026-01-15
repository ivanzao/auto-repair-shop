# Stage 1: Build fat jar
FROM gradle:8.14-jdk21 AS builder

WORKDIR /app

COPY . .

RUN ./gradlew :main:shadowJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/main/build/libs/application.jar /app/application.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
