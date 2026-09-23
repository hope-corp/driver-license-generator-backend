# Stage 1: Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon -x test

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the generated JAR (matches both standard and shadow JARs)
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
