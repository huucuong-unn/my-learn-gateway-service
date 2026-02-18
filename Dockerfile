FROM eclipse-temurin:21-jdk-alpine as build
LABEL authors="MyLearn Senior Engineer"

ARG GPR_USERNAME
ARG GPR_PASSWORD

WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

RUN mkdir -p /root/.m2 \
    && echo '<settings><servers><server><id>github</id><username>${GPR_USERNAME}</username><password>${GPR_PASSWORD}</password></server></servers></settings>' > /root/.m2/settings.xml

COPY src /app/src

RUN chmod +x mvnw \
    && ./mvnw clean package -DskipTests

RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

RUN rm /root/.m2/settings.xml


FROM eclipse-temurin:21-jre-alpine
LABEL authors="MyLearn Senior Engineer"

# Set the application owner user for security best practices
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Set the working directory for the application
WORKDIR /app

# Copy only the necessary layers from the build stage for an optimized JAR execution
COPY --from=build /app/target/*.jar /app/app.jar

# Expose the application port (The Gateway runs on 8000 locally)
EXPOSE 8000

# The command to run the application when the container starts
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
