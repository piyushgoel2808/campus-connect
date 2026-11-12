# ==============================================================================
# Campus Connect - Multi-Stage Dockerfile
# ==============================================================================
# Purpose:
#   Builds and packages the Campus Connect Spring Boot application into a
#   lightweight, production-ready container image using a two-stage build:
#     1. Build Stage: Compiles Java source code and packages the fat JAR using Maven.
#     2. Run Stage:   Runs the compiled JAR on a minimal Eclipse Temurin JRE 17 image.
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Build Stage (Maven + JDK 17)
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies offline so Docker can cache the
# Maven dependency layer across builds whenever pom.xml has not changed.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the application source code and package the executable Spring Boot JAR
# (-DskipTests speeds up image builds; -B runs Maven in non-interactive batch mode).
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# 2. Run Stage (Minimal JRE 17 Runtime)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy only the built JAR artifact from the build stage to keep the final image small
COPY --from=build /app/target/*.jar app.jar

# Expose the default Spring Boot HTTP port
EXPOSE 8080

# ==============================================================================
# 🚀 MEMORY OPTIMIZATION (Render Free Tier 512Mi cgroup limit)
# -Djava.awt.headless=true   : Enables PDFBox image rendering inside headless containers.
# -XX:+UseSerialGC           : Low-overhead collector suited to small containers (512Mi).
# -Xms128m / -Xmx240m        : Leaves headroom for Metaspace, CodeCache, threads & AWT/ImageIO buffers.
# -Xss256k                   : Reduces per-thread stack native footprint.
# -XX:MaxMetaspaceSize=160m  : Bounds class metadata native memory.
# -XX:ReservedCodeCacheSize=40m & -XX:MaxDirectMemorySize=32m : Bounds JIT & NIO direct buffers.
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:+UseSerialGC", "-Xms128m", "-Xmx240m", "-Xss256k", "-XX:MaxMetaspaceSize=160m", "-XX:ReservedCodeCacheSize=40m", "-XX:MaxDirectMemorySize=32m", "-jar", "app.jar"]