# ---- build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first: this layer is cached and only rebuilt when the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
