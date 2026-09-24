# Multi-stage build for KASE Stock Exchange Parser
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy maven wrapper & pom first for Docker layer caching
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw
RUN ./mvnw dependency:resolve -B || true

# Copy sources and package
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Minimal runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install fontconfig and freetype for headless PDF & chart rendering
RUN apk add --no-cache fontconfig freetype ttf-dejavu

# Run as non-privileged user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
