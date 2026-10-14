# Build multi-module (JDK 17+ to compile; outputs target Java 8 / 8 / 11).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY libs/ ./libs/
COPY core/ ./core/
COPY desktop/ ./desktop/
COPY web/ ./web/
RUN mvn -q -DskipTests package && find . -name dependency-reduced-pom.xml -delete

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/web/target/merger-web.jar /app/merger-web.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/merger-web.jar", "--port", "8080"]
