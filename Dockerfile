FROM eclipse-temurin:21-jre

RUN addgroup --system app && adduser --system --ingroup app app

WORKDIR /app

COPY main/build/libs/application.jar application.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
