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

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]