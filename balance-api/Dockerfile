# syntax=docker/dockerfile:1
# Build context is the repository root: the module build needs the parent POM.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2     ./mvnw -B -q -pl balance-api -am package -DskipTests     && java -Djarmode=tools -jar balance-api/target/balance-api-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /extracted/dependencies/ ./
COPY --from=build --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /extracted/application/ ./
USER app
EXPOSE 8080 8081
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=10   CMD wget -q -O /dev/null http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
