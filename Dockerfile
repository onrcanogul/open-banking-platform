# Builds one service: docker build --build-arg MODULE=api-gateway -t obe/api-gateway:0.1.0 .
# The build stage is shared, so the other services reuse it from the layer cache.
FROM eclipse-temurin:17.0.20_8-jdk-noble AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:17.0.20_8-jre-noble
ARG MODULE
WORKDIR /app
COPY --from=build /src/services/${MODULE}/target/${MODULE}-*.jar app.jar
# A numeric user, so the cluster can prove it is not root
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
