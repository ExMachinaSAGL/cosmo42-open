# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests clean package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home --uid 1001 cosmo42 \
    && mkdir -p /var/lib/cosmo42 \
    && chown -R cosmo42:cosmo42 /var/lib/cosmo42

COPY --from=build /workspace/app.jar /app/app.jar

USER cosmo42
EXPOSE 8080

ENV JAVA_OPTS="" \
    COSMO42_STORAGE_PATH=/var/lib/cosmo42

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
