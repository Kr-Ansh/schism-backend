# Stage 1: Build your Gradle application using an official Ubuntu image environment
FROM ubuntu:22.04 AS build
WORKDIR /app

# Install standard networking utilities and compiler tools
RUN apt-get update && apt-get install -y curl unzip wget

# Download and install Eclipse Temurin OpenJDK 26 manually inside the build engine
RUN wget https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26%2B0/OpenJDK26-jdk_x64_linux_hotspot_26_0.tar.gz && \
    tar -xzf OpenJDK26-jdk_x64_linux_hotspot_26_0.tar.gz && \
    mv jdk-26* /opt/java26

# Assign environment routing paths to point to our newly installed Java 26 home
ENV JAVA_HOME=/opt/java26
ENV PATH="$JAVA_HOME/bin:$PATH"

# Copy your local configuration project files over to the working layout directory
COPY build.gradle settings.gradle* gradlew ./
COPY gradle ./gradle
COPY src ./src

# Compile the target production binary standalone jar
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Deploy using a clean base distribution layer
FROM ubuntu:22.04
WORKDIR /app

# Copy the Java 26 installation directly out of the build stage to keep runtime lightweight
COPY --from=build /opt/java26 /opt/java26
ENV JAVA_HOME=/opt/java26
ENV PATH="$JAVA_HOME/bin:$PATH"

# Copy the freshly compiled Gradle executable standalone fat JAR
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]