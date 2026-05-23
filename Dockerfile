# Stage 1: Build using an official Ubuntu environment
FROM ubuntu:22.04 AS build
WORKDIR /app

# 1. Install necessary security and archive utilities
RUN apt-get update && apt-get install -y wget apt-transport-https gnupg

# 2. Register Adoptium's official security GPG key ring and software repository
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add - && \
    echo "deb https://packages.adoptium.net/artifactory/deb jammy main" | tee /etc/apt/sources.list.d/adoptium.list

# 3. Update repositories and install the native Java 26 JDK compilation toolchain package
RUN apt-get update && apt-get install -y temurin-26-jdk

# Define standard system routing variables pointing to the new Java 26 installation location
ENV JAVA_HOME=/usr/lib/jvm/temurin-26-jdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Copy local Gradle wrapper and source modules
COPY build.gradle settings.gradle* gradlew ./
COPY gradle ./gradle
COPY src ./src

# Compile the production executable fat JAR
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Deploy using a clean, ultra-lightweight distribution layer
FROM ubuntu:22.04
WORKDIR /app

# Install runtime utilities, fetch Adoptium keys, and install the headless JRE 26 for production execution
RUN apt-get update && apt-get install -y wget apt-transport-https gnupg && \
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add - && \
    echo "deb https://packages.adoptium.net/artifactory/deb jammy main" | tee /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && apt-get install -y temurin-26-jre

ENV JAVA_HOME=/usr/lib/jvm/temurin-26-jre-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Copy the compiled JAR artifact straight out of the build container sandbox
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]