FROM eclipse-temurin:21.0.12_8-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY server/mvnw server/pom.xml ./
COPY server/.mvn/ .mvn/
COPY server/src/ src/
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21.0.12_8-jre-jammy
RUN useradd --system --uid 10001 --create-home toolbox
WORKDIR /app
COPY --from=build --chown=toolbox:toolbox /workspace/target/toolbox-api-0.1.0-SNAPSHOT.jar app.jar
USER toolbox
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
