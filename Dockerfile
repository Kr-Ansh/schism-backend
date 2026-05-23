# Stage 1: Build the application using Gradle
FROM gradle:8-jdk17 AS build
WORKDIR /app

# FIX 1: Use wildcards so it grabs settings.gradle OR settings.gradle.kts automatically!
COPY build.gradle settings.gradle* ./
COPY src ./src

# FIX 2: Explicitly pass the bootJar command and disable the plain jar build artifact
RUN gradle bootJar -x test --no-daemon

# Stage 2: Create the lightweight production container runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# FIX 3: Point directly to the bootJar build directory location layout
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]