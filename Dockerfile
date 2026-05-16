# --------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Use the Maven wrapper so the host doesn't need Maven installed.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B clean package -DskipTests

# --------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root for security.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/target/log-summarizer-*.jar app.jar

EXPOSE 8080

# JVM tuned for containers; container-aware ergonomics ON by default in JDK 21.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Health probe for Docker/Kubernetes.
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
