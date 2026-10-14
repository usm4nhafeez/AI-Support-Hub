# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 — build. A throwaway container with Maven + JDK 27 compiles the
# project and produces the runnable jar. Nothing from this stage ships.
# ---------------------------------------------------------------------------
FROM maven:3-eclipse-temurin-27 AS build
WORKDIR /build

# Dependencies are resolved from pom.xml alone, so this expensive layer is only
# rebuilt when a dependency changes — editing code under src/ never re-downloads.
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

# Now the sources, and the actual build. Tests are skipped: the single
# @SpringBootTest needs a live Oracle database plus four environment variables.
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------------------------------------------------------------------------
# Stage 2 — run. Only the JRE plus the jar from stage 1, so the image you
# deploy stays small and carries no compiler, no sources, no Maven cache.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:27-jre AS runtime
WORKDIR /app

# Containers should not run as root. Some hosts refuse to start an image whose
# entrypoint is root. -r = system account, -g = its group, -d = home, -s = no shell.
RUN groupadd -r spring && useradd -r -g spring -d /app -s /usr/sbin/nologin spring \
    && chown spring:spring /app

# The jar name is fixed by pom.xml (<artifactId>-<version>.jar). Copying it to a
# stable name means the start command never changes when the version does.
COPY --from=build --chown=spring:spring /build/target/ai-support-hub-0.0.1-SNAPSHOT.jar app.jar

USER spring

# Local runs default to port 8000. Deployment platforms can override PORT with
# the port assigned to the service.
EXPOSE 8000
ENV PORT=8000

# Leave memory outside the Java heap for Spring, the JDBC driver, and the
# container runtime. This is important on small deployment plans.
# Override at run time with:  docker run -e JAVA_OPTS="..." ...
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=15.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar ${PORT:+--server.port=$PORT}"]
