# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependencies first, so they are cached across builds that only change source code
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests run in development/CI; the image build only packages.
RUN ./mvnw -B -q -DskipTests package && cp target/toll-route-finder-*.jar app.jar

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/app.jar app.jar
USER app
# Render injects PORT; the app reads it (defaults to 8080 locally).
EXPOSE 8080
# Fit Render's free instance (512 MB): cap the heap relative to the container and use the lean serial GC.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
