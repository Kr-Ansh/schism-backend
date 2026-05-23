# Stage 1: Build the Maven application package
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvcw clean package -DskipTests || mvn clean package -DskipTests

# Stage 2: Spin up the slim production container runtime environment
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose Render's dynamic port allocation hook environment variable
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]