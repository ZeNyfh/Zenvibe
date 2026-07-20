# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Resolve dependencies in a cacheable layer before copying the source tree.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/target/bot-*.jar /app/bot.jar
COPY locales /app/locales

# Zenvibe writes guild settings and queue recovery data here. Mount the
# Railway persistent volume at /app/config.
RUN mkdir -p /app/config /app/logs /app/temp /app/update

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

STOPSIGNAL SIGTERM
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/bot.jar"]
