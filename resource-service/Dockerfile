# 1. Build Stage
FROM eclipse-temurin:17-alpine AS build

WORKDIR /app

RUN apk add --no-cache maven

COPY pom.xml ./

RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean package -Dmaven.test.skip=true


# 2. Runtime Stage
FROM eclipse-temurin:17-alpine

WORKDIR /app

RUN mkdir -p /app/logs

RUN apk add --no-cache curl

COPY --from=build /app/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]