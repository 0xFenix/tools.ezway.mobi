# Build + run with the same fat jar as desktop (no new deps).
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml libs/ ./libs/
COPY src/ ./src/
RUN mvn -q -DskipTests package && rm -f dependency-reduced-pom.xml

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/merger.jar /app/merger.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/merger.jar", "--serve", "--port", "8080"]
