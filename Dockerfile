# Stage 1: Build the Maven dependencies and package the fat JAR
FROM docker.io/library/maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY . .
RUN mvn clean package -f backend/pom.xml -DskipTests

# Stage 2: Run environment using eclipse-temurin
FROM docker.io/library/eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the compiled fat JAR from build stage
COPY --from=build /app/backend/target/*-jar-with-dependencies.jar app.jar
# Copy the frontend folder so the launcher's resource handler can serve it
COPY --from=build /app/frontend ./frontend

EXPOSE 8080
ENV GOOGLE_API_KEY=${GOOGLE_API_KEY}

ENTRYPOINT ["java", "-jar", "app.jar"]
