# Stage 1: Build the Spring Boot JAR using Maven + JDK 25
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Lightweight JRE 25 runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8085

# Cap heap to 350MB so it runs smoothly inside Render's 512MB RAM free tier
ENTRYPOINT ["java", "-Xmx350m", "-Xms128m", "-jar", "app.jar"]