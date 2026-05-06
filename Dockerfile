FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Cố tình sử dụng bản OpenJDK 8 rất cũ để chứa nhiều lỗ hổng
FROM openjdk:8u111-jre-alpine

WORKDIR /app
COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
