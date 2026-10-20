FROM gradle:9.8.0-jdk21 AS build
WORKDIR /home/gradle
COPY --chown=gradle:gradle . project
WORKDIR /home/gradle/project
RUN gradle :app-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /home/gradle/project/applications/app-service/build/libs/bancoias.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
