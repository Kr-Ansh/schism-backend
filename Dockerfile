# Stage 1: Build the application using Gradle
FROM gradle:8-jdk17 AS build
WORKDIR /app

# Copy the build configuration files first to leverage Docker layer caching
COPY build.gradle settings.gradle ./

# Copy the actual application source code
COPY src ./src

# Compile the production executable fat-JAR file, bypassing verification checks
RUN gradle bootJar -X lint:none -x test

# Stage 2: Create the lightweight production container runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the compiled .jar file straight out of the Gradle build stage environment
COPY --from=build /app/build/libs/*.jar app.jar

# Expose Render's dynamic web service port hook
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]