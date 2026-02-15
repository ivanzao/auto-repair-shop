FROM eclipse-temurin:21-jre

WORKDIR /app

COPY main/build/libs/application.jar application.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
