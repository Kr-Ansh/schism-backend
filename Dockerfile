# Stage 1: Build the application using Gradle with Java 26 support
FROM gradle:jdk-26 AS build
WORKDIR /app

# Copy configuration files to utilize cache matching rules
COPY build.gradle settings.gradle* ./
COPY src ./src

# Execute compilation using explicit error tracking flags
RUN gradle bootJar -x test --no-daemon --stacktrace --warning-mode all

# Stage 2: Create a lightweight runtime environment using Java 26
FROM openjdk:26-jdk-slim
WORKDIR /app

# Point directly to the compiled .jar artifact destination folder
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]